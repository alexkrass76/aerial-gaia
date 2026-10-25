package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import dalvik.system.PathClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Uses only the installed firmware API. Writes are fixed, checked and journalled before dispatch. */
internal object GaiaAvSettings {
    private const val API = "com.cvte.tv.api.aidl."
    private const val LIBRARY = "/system/framework/cn.cvte.tv.api.jar"
    private val component = ComponentName("com.cvte.tv.api.impl", "com.cvte.tv.api.TvApiService")
    private const val JOURNAL = "last_av_change"

    @Synchronized fun snapshot(context: Context): JSONObject = withSession(context) { it.snapshot() }
    fun hasUndo(context: Context): Boolean = context.getSharedPreferences("gaia_av", 0).contains(JOURNAL)

    @Synchronized fun change(context: Context, key: String, value: String, expected: JSONObject) {
        check(!app.orchard.tv.settings.AerialSettings.locked)
        withSession(context) { session ->
            val before = session.snapshot()
            GaiaAvPolicy.validate(before, expected, key, value)
            val previous = GaiaAvPolicy.value(before, key)
            if (previous == value) return@withSession
            val record = JSONObject().put("before", before).put("key", key).put("old", previous).put("new", value)
            // Commit before touching firmware; this also survives activity/process recreation.
            check(context.getSharedPreferences("gaia_av", 0).edit().putString(JOURNAL, record.toString()).commit())
            check(!app.orchard.tv.settings.AerialSettings.locked)
            check(session.write(key, value)) { "Firmware refused" }
            check(GaiaAvPolicy.value(session.snapshot(), key) == value) { "Read-back mismatch" }
        }
    }

    @Synchronized fun undo(context: Context) {
        check(!app.orchard.tv.settings.AerialSettings.locked)
        val prefs = context.getSharedPreferences("gaia_av", 0)
        val record = JSONObject(requireNotNull(prefs.getString(JOURNAL, null)))
        withSession(context) { session ->
            val current = session.snapshot()
            val before = record.getJSONObject("before")
            val key = record.getString("key")
            val old = record.getString("old")
            check(current.getString("source") == before.getString("source")) { "Input changed" }
            val actual = GaiaAvPolicy.value(current, key)
            check(actual == old || actual == record.getString("new")) { "Setting changed externally" }
            if (actual != old) {
                if (key.startsWith("PICTURE_") || key == "backlight") check(current.getJSONObject("picture").getString("mode") == before.getJSONObject("picture").getString("mode"))
                GaiaAvPolicy.validate(current, current, key, old, restoring = true)
                check(session.write(key, old)) { "Restore refused" }
                check(GaiaAvPolicy.value(session.snapshot(), key) == old) { "Restore mismatch" }
            }
            check(prefs.edit().remove(JOURNAL).commit())
        }
    }

    private fun <T> withSession(context: Context, block: (Session) -> T): T {
        check(Looper.myLooper() != Looper.getMainLooper())
        check(File(LIBRARY).isFile) { "Firmware API unavailable" }
        val app = context.applicationContext
        val loader = PathClassLoader(LIBRARY, app.classLoader)
        val binder = AtomicReference<IBinder?>()
        val ready = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder.set(service); ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { binder.set(null) }
            override fun onNullBinding(name: ComponentName) { ready.countDown() }
        }
        val bound = app.bindService(Intent("com.cvte.tv.api.TV_API_SERVICE").setComponent(component), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Firmware service unavailable" }
        try {
            check(ready.await(5, TimeUnit.SECONDS)) { "Firmware service timeout" }
            val service = requireNotNull(binder.get())
            check(service.interfaceDescriptor == API + "ITvApiManager")
            val manager = Class.forName(API + "ITvApiManager\$Stub", false, loader)
                .getMethod("asInterface", IBinder::class.java).invoke(null, service)
            return block(Session(loader, requireNotNull(manager)))
        } finally { app.unbindService(connection) }
    }

    private class Session(val loader: ClassLoader, val manager: Any) {
        fun type(name: String) = Class.forName(API + name, false, loader)
        fun group(name: String): Any = requireNotNull(type("ITvApiManager").getMethod("getTVApi$name").invoke(manager))
        fun get(target: Any, name: String, method: String): Any? = type("ITVApi${name}Aidl").getMethod(method).invoke(target)
        fun enumName(value: Any?): String? = (value as? Enum<*>)?.name
        fun names(value: Any?): JSONArray = JSONArray((value as? List<*>)?.mapNotNull(::enumName).orEmpty())
        fun enumValue(name: String, value: String): Any = requireNotNull(type(name).enumConstants?.firstOrNull { enumName(it) == value })
        fun write(key: String, value: String): Boolean {
            return when (key) {
                "backlight" -> type("ITVApiSystemBacklightAidl").getMethod("eventSystemBacklightDimmingPWMSetDuty", Int::class.javaPrimitiveType)
                    .invoke(group("SystemBacklight"), value.toInt()) == true
                "picture_mode" -> type("ITVApiPictureControlAidl").getMethod("eventSetCurrentPictureMode", type("EnumModeOptionId"))
                    .invoke(group("PictureControl"), enumValue("EnumModeOptionId", value)) == true
                "digital" -> type("ITVApiSoundSpdifOutAidl").getMethod("eventSoundSpdifOutSetMode", type("EnumSoundSpdifOutMode"))
                    .invoke(group("SoundSpdifOut"), enumValue("EnumSoundSpdifOutMode", value)) == true
                in GaiaAvPolicy.pictureKeys -> type("ITVApiPictureControlAidl")
                    .getMethod("eventPictureControlSetValue", type("EnumPictureControlItem"), Int::class.javaPrimitiveType)
                    .invoke(group("PictureControl"), enumValue("EnumPictureControlItem", key), value.toInt()) == true
                else -> error("Unsupported setting")
            }
        }
        fun snapshot(): JSONObject {
            val result = JSONObject()
            runCatching {
                val input = requireNotNull(get(group("SystemInputSource"), "SystemInputSource", "eventSystemInputSourceGetInputSource"))
                val inputType = type("EntityInputSource")
                result.put("source", "${inputType.getField("id").getInt(input)}:${enumName(inputType.getField("category").get(input))}")
            }
            // Fail each group independently: one unsupported interface must not hide the rest.
            runCatching {
                val target = group("PictureControl")
                val picture = JSONObject().put("mode", enumName(get(target, "PictureControl", "eventGetCurrentPictureMode")))
                    .put("modes", names(get(target, "PictureControl", "eventGetPictureModeOptions")))
                val itemType = type("EnumPictureControlItem")
                val api = type("ITVApiPictureControlAidl")
                val items = JSONArray()
                for (item in itemType.enumConstants.orEmpty().take(5)) {
                    val value = api.getMethod("eventPictureControlGetValue", itemType).invoke(target, item) as? Int
                    val status = enumName(api.getMethod("eventPictureControlGetItemStatus", itemType).invoke(target, item))
                    if (value != null && value in 0..100) items.put(JSONObject().put("key", enumName(item)).put("value", value).put("status", status))
                }
                picture.put("items", items)
                result.put("picture", picture)
            }
            runCatching {
                val target = group("SystemBacklight")
                val value = get(target, "SystemBacklight", "eventSystemBacklightDimmingPWMGetDuty") as? Int
                if (value != null && value in 0..100) result.put("backlight", JSONObject().put("value", value)
                    .put("status", enumName(get(target, "SystemBacklight", "eventSystemBacklightGetItemStatus"))))
            }
            runCatching {
                val target = group("SoundOut")
                result.put("sound", JSONObject().put("mode", enumName(get(target, "SoundOut", "eventGetSoundOutMode")))
                    .put("modes", names(get(target, "SoundOut", "eventGetSoundOutList"))))
            }
            runCatching {
                val target = group("SoundSpdifOut")
                result.put("digital", JSONObject().put("mode", enumName(get(target, "SoundSpdifOut", "eventSoundSpdifOutGetMode")))
                    .put("modes", names(get(target, "SoundSpdifOut", "eventSoundSpdifOutGetModeList"))))
            }
            check(result.length() > 0) { "No supported firmware reads" }
            return result
        }
    }
}
