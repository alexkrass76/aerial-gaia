package app.orchard.tv.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import app.orchard.tv.tasks.LocalAdbTransport
import org.json.JSONObject

/** On-demand scalar backend. Fixed setters, journalled in app and revalidated in shell. */
internal object MediaTekAvReader {
    fun available(context: Context): Boolean = Build.VERSION.SDK_INT == 30 && runCatching {
        context.packageManager.getPackageInfo("com.mediatek.tvinput", 0)
        val flags = context.packageManager.getApplicationInfo("com.yandex.tv.settings", 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    // PackageManager supplies both paths; quote defensively, never accept UI/sender shell text.
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    @Synchronized fun snapshot(context: Context): JSONObject = request(context)

    private fun request(context: Context, write: JSONObject? = null): JSONObject {
        check(available(context)) { "Unsupported firmware" }
        val apk = context.applicationInfo.sourceDir
        val vendor = context.packageManager.getApplicationInfo("com.yandex.tv.settings", 0).sourceDir
        val command = "CLASSPATH=${quote(apk)} /system/bin/timeout 8 /system/bin/app_process /system/bin " +
            "aerial.probe.MediaTekReadProbe ${quote(vendor)}" + (write?.let { " " + quote(it.toString()) } ?: "")
        val result = LocalAdbTransport.connect(12_000).use { it.shell(command) }
        val line = result.lineSequence().firstOrNull { it.trim().startsWith("{\"uid\"") }
            ?: error("No getter response")
        val root = JSONObject(line)
        check(root.optInt("uid", -1) == 2000 && root.optInt("sdk", -1) == 30) { "Unexpected helper identity" }
        val items = root.optJSONArray("items") ?: error("Getter unavailable")
        check((0 until items.length()).any { !items.getJSONObject(it).has("error") }) { "All getters unavailable" }
        return root
    }
    @Synchronized fun cecSnapshot(context: Context): JSONObject {
        check(available(context)) { "Unsupported firmware" }
        // Fixed diagnostic command. Drop history before it reaches the app; no CEC commands.
        val dump = LocalAdbTransport.connect(8_000).use {
            it.shell("/system/bin/dumpsys -t 4 hdmi_control | /system/bin/sed '/CEC message history:/,\$d'")
        }
        return JSONObject().put("cec", YaosCecState.parse(dump))
    }
    /** Dedicated night journal is committed by NightBacklight before dispatch. Does not replace manual Undo. */
    @Synchronized fun nightBacklight(context: Context, value: Int, expected: JSONObject): JSONObject {
        return request(context, JSONObject().put("key", "g_disp__disp_back_light").put("value", value).put("expected", expected))
    }
    private const val JOURNAL = "last_change"
    fun hasUndo(context: Context) = context.getSharedPreferences("mediatek_av", 0).contains(JOURNAL)
    @Synchronized fun change(context: Context, key: String, target: Int, expected: JSONObject) {
        check(!app.orchard.tv.settings.AerialSettings.locked)
        val prefs = context.getSharedPreferences("mediatek_av", 0)
        val previous = prefs.getString(JOURNAL, null)?.let(::JSONObject)
        check(previous == null || previous.optBoolean("verified")) { "Resolve pending rollback first" }
        val before = snapshot(context)
        aerial.probe.MediaTekWritePolicy.validate(before, expected, key, target)
        val old = aerial.probe.MediaTekWritePolicy.value(before, key)
        if (old == target) return
        val record = JSONObject().put("before", before).put("key", key).put("old", old).put("new", target)
        check(prefs.edit().putString(JOURNAL, record.toString()).commit()) { "Journal failed" }
        check(!app.orchard.tv.settings.AerialSettings.locked)
        request(context, JSONObject().put("key", key).put("value", target).put("expected", before))
        val after = snapshot(context)
        aerial.probe.MediaTekWritePolicy.context(after, before)
        check(aerial.probe.MediaTekWritePolicy.value(after,key) == target) { "Read-back mismatch" }
        record.put("verified", true)
        check(prefs.edit().putString(JOURNAL, record.toString()).commit())
    }
    @Synchronized fun undo(context: Context) {
        check(!app.orchard.tv.settings.AerialSettings.locked)
        val prefs = context.getSharedPreferences("mediatek_av", 0)
        val record = JSONObject(requireNotNull(prefs.getString(JOURNAL, null)))
        val current = snapshot(context)
        val key = record.getString("key")
        val old = record.getInt("old")
        aerial.probe.MediaTekWritePolicy.context(current, record.getJSONObject("before"))
        val value = aerial.probe.MediaTekWritePolicy.value(current,key)
        check(value == old || value == record.getInt("new")) { "Setting changed externally" }
        if(value != old) request(context, JSONObject().put("key", key).put("value", old).put("expected", current))
        val restored = snapshot(context)
        aerial.probe.MediaTekWritePolicy.context(restored, current)
        check(aerial.probe.MediaTekWritePolicy.value(restored,key) == old) { "Restore mismatch" }
        check(prefs.edit().remove(JOURNAL).commit())
    }

}

/** No enum guesses, no zero-as-off fallback, no conversion to writable settings. */
internal object MediaTekReadValue {
    fun scalar(item: JSONObject): Int? {
        if (item.has("error") || !item.has("raw") || !item.has("rangeRaw")) return null
        val packed = item.getInt("rangeRaw")
        if (packed == 0) return null
        val low = (packed and 0xffff).toShort().toInt()
        val high = ((packed ushr 16) and 0xffff).toShort().toInt()
        val value = item.getInt("raw")
        return value.takeIf { low <= high && it in low..high }
    }
}
