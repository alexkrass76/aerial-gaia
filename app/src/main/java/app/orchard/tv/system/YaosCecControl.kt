package app.orchard.tv.system

import android.content.Context
import app.orchard.tv.tasks.LocalAdbTransport
import app.orchard.tv.settings.AerialSettings
import org.json.JSONObject

internal object YaosCecPolicy {
    val keys = linkedMapOf("enabled" to "hdmi_control_enabled",
        "auto_off" to "hdmi_control_auto_device_off_enabled",
        "auto_wake" to "hdmi_control_auto_wakeup_enabled",
        "volume" to "hdmi_control_volume_control_enabled")
    fun storedMatchesRuntime(state: JSONObject, key: String): Boolean {
        val stored = state.getJSONObject("cec_stored").getString(key)
        // Verified Android 11 defaults are enabled when the key is absent.
        return stored in setOf("null","0","1") && state.getJSONObject("cec").opt(key) == (stored != "0")
    }
    fun validate(current: JSONObject, expected: JSONObject, key: String) {
        require(key in keys)
        val live = current.getJSONObject("cec")
        val prior = expected.getJSONObject("cec")
        check(live.opt(key) is Boolean && prior.opt(key) is Boolean) { "Unknown runtime state" }
        check(live.getBoolean(key) == prior.getBoolean(key)) { "Runtime changed externally" }
        val stored = current.getJSONObject("cec_stored").getString(key)
        check(stored in setOf("null", "0", "1") && stored == expected.getJSONObject("cec_stored").getString(key)) { "Stored value changed" }
        check(live.opt("enabled") is Boolean) { "CEC state unknown" }
        if (key != "enabled") check(live.getBoolean("enabled")) { "Enable CEC first" }
        // Do not disrupt an active audio receiver. Disabled CEC may omit these fields.
        if (live.getBoolean("enabled")) check(live.opt("arc_established") == false && live.opt("system_audio") == false) { "Active or unknown audio receiver" }
    }
}

/** Fixed Global keys whose ContentObserver/runtime roundtrips were verified on this firmware. */
internal object YaosCecControl {
    private const val JOURNAL = "last_change"
    private fun prefs(context: Context) = context.getSharedPreferences("yaos_cec",0)
    fun hasUndo(context: Context) = prefs(context).contains(JOURNAL)
    @Synchronized fun snapshot(context: Context): JSONObject {
        val root = MediaTekAvReader.cecSnapshot(context)
        val command = YaosCecPolicy.keys.values.joinToString("; ") { "/system/bin/settings get global $it" }
        val lines = LocalAdbTransport.connect(8_000).use { it.shell(command) }.trim().lines()
        check(lines.size == YaosCecPolicy.keys.size && lines.all { it in setOf("null","0","1") }) { "CEC settings unavailable" }
        val stored = JSONObject()
        YaosCecPolicy.keys.keys.zip(lines).forEach { (key,value) -> stored.put(key,value) }
        return root.put("cec_stored",stored)
    }
    private fun dispatch(key: String, expected: String, target: String) {
        val name = requireNotNull(YaosCecPolicy.keys[key])
        require(expected in setOf("null","0","1") && target in setOf("null","0","1"))
        val update = if(target == "null") "/system/bin/settings delete global $name" else "/system/bin/settings put global $name $target"
        val command = "if [ \"\$(/system/bin/settings get global $name)\" = '$expected' ]; then $update >/dev/null && echo AERIAL_CEC_OK; fi"
        check(LocalAdbTransport.connect(8_000).use { it.shell(command) }.trim() == "AERIAL_CEC_OK") { "CEC write refused" }
    }
    private fun verify(context: Context, key: String, stored: String, runtime: Boolean) {
        val deadline = System.nanoTime() + 8_000_000_000L
        do {
            val actual = snapshot(context)
            if(actual.getJSONObject("cec_stored").getString(key) == stored && actual.getJSONObject("cec").opt(key) == runtime) return
            Thread.sleep(250)
        } while(System.nanoTime() < deadline)
        error("CEC runtime read-back mismatch")
    }
    @Synchronized fun change(context: Context, key: String, target: Boolean, expected: JSONObject) {
        check(!AerialSettings.locked)
        val previous = prefs(context).getString(JOURNAL,null)?.let(::JSONObject)
        check(previous == null || previous.optBoolean("verified")) { "Resolve pending rollback" }
        val current = snapshot(context)
        YaosCecPolicy.validate(current,expected,key)
        check(YaosCecPolicy.storedMatchesRuntime(current,key)) { "Stored/runtime state inconsistent" }
        val old = current.getJSONObject("cec").getBoolean(key)
        if(old == target) return
        val stored = current.getJSONObject("cec_stored").getString(key)
        val record = JSONObject().put("key",key).put("old",old).put("stored",stored).put("new",target)
        check(prefs(context).edit().putString(JOURNAL,record.toString()).commit())
        check(!AerialSettings.locked)
        dispatch(key,stored,if(target) "1" else "0")
        verify(context,key,if(target) "1" else "0",target)
        record.put("verified",true)
        check(prefs(context).edit().putString(JOURNAL,record.toString()).commit())
    }
    @Synchronized fun undo(context: Context) {
        check(!AerialSettings.locked)
        val record = JSONObject(requireNotNull(prefs(context).getString(JOURNAL,null)))
        val key = record.getString("key")
        val current = snapshot(context)
        YaosCecPolicy.validate(current,current,key)
        val stored = current.getJSONObject("cec_stored").getString(key)
        val live = current.getJSONObject("cec").getBoolean(key)
        val oldStored = record.getString("stored")
        val old = record.getBoolean("old")
        val changed = record.getBoolean("new")
        check((stored == oldStored && live == old) || (stored == (if(changed) "1" else "0") && (live == changed || (!record.optBoolean("verified") && live == old)))) { "CEC changed externally" }
        if(stored != oldStored) dispatch(key,stored,oldStored)
        verify(context,key,oldStored,old)
        check(prefs(context).edit().remove(JOURNAL).commit())
    }
}
