package app.orchard.tv.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.orchard.tv.apps.TileSheet
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.NameMode
import app.orchard.tv.system.YaosCecControl
import app.orchard.tv.system.MediaTekAvReader
import app.orchard.tv.system.DiagLog
import app.orchard.tv.update.Updates
import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.launch

/**
 * Options set from a computer over ADB, e.g.
 *   adb shell am broadcast -a app.orchard.tv.CONFIG -n app.orchard.tv/.guard.ConfigReceiver --ez keep_adb true
 *   ... --ez own_screensaver true   (keep Aerial as the system screensaver; see GuardSetup)
 *   ... --ez guard_off_in_standby true  (Home Guard leaves at screen off, back at screen on;
 *                                   for TVs that do not wake with it enabled, issue #371)
 *   ... --ez clear_pin true         (turn the parental PIN off, for an owner who forgot it)
 *   ... --ez large_clock true --ez clock_right false --ez one_row_home true
 *       --ez peek_next_row false --ez home_plate true --ez show_all_names false
 *                                   (Home's layout, as in Personalize; for screenshots of each)
 *   ... --ez check_update true      (look for a new version now, not within three hours)
 *   ... --ez update_test true       ("Download and Install" takes files/update-test.apk instead,
 *                                   until Aerial restarts; for trying an update before a release)
 * The manifest requires the sender to hold WRITE_SECURE_SETTINGS, which the ADB shell has and
 * ordinary apps cannot get, so no other app on the TV can flip these.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("yaos_cec_roundtrip_test", false)) {
            val pending=goAsync()
            val app=context.applicationContext
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val report=org.json.JSONObject()
                try {
                    check(!YaosCecControl.hasUndo(app))
                    val rows=org.json.JSONArray()
                    for(key in listOf("auto_off","auto_wake","volume","enabled")) {
                        val before=YaosCecControl.snapshot(app)
                        val old=before.getJSONObject("cec").getBoolean(key)
                        try { YaosCecControl.change(app,key,!old,before) }
                        finally { if(YaosCecControl.hasUndo(app)) YaosCecControl.undo(app) }
                        val after=YaosCecControl.snapshot(app)
                        check(after.getJSONObject("cec").getBoolean(key)==old)
                        check(after.getJSONObject("cec_stored").getString(key)==before.getJSONObject("cec_stored").getString(key))
                        rows.put(org.json.JSONObject().put("key",key).put("restored",old).put("stored",after.getJSONObject("cec_stored").getString(key)))
                    }
                    report.put("ok",true).put("roundtrips",rows)
                } catch(e: Exception) { report.put("ok",false).put("error",e.toString()) }
                finally {
                    java.io.File(app.getExternalFilesDir(null),"yaos-cec-roundtrip.json").writeText(report.toString())
                    pending.finish()
                }
            }
            return
        }
        // Owner-triggered integration check: one-step writes followed immediately by undo.
        if (intent.getBooleanExtra("mediatek_roundtrip_test", false) || intent.getBooleanExtra("mediatek_backlight_roundtrip_test", false)) {
            val pending = goAsync()
            val app = context.applicationContext
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val report = org.json.JSONObject()
                try {
                    check(!MediaTekAvReader.hasUndo(app)) { "Existing undo must be preserved" }
                    val rows = org.json.JSONArray()
                    val keys = if (intent.getBooleanExtra("mediatek_backlight_roundtrip_test", false)) listOf("g_disp__disp_back_light")
                        else listOf("g_video__brightness", "g_video__contrast", "g_video__vid_sat",
                            "g_video__vid_shp", "g_video__vid_hue", "g_audio__aud_balance", "g_disp__disp_back_light")
                    for (key in keys) {
                        val before = MediaTekAvReader.snapshot(app)
                        val old = aerial.probe.MediaTekWritePolicy.value(before, key)
                        val target = old + if (old < (if (key == "g_audio__aud_balance") 50 else 100)) 1 else -1
                        try {
                            MediaTekAvReader.change(app, key, target, before)
                        } finally {
                            if (MediaTekAvReader.hasUndo(app)) MediaTekAvReader.undo(app)
                        }
                        val after = MediaTekAvReader.snapshot(app)
                        check(aerial.probe.MediaTekWritePolicy.value(after,key) == old)
                        rows.put(org.json.JSONObject().put("key",key).put("before",old).put("tested",target).put("restored",old))
                    }
                    report.put("roundtrips", rows).put("ok",true)
                } catch (e: Exception) { report.put("ok",false).put("error", e.toString()) }
                finally {
                    java.io.File(app.getExternalFilesDir(null), "mediatek-roundtrip.json").writeText(report.toString())
                    pending.finish()
                }
            }
            return
        }
        // Explicit, permission-guarded diagnostic. Normal startup never loads vendor code.
        if (intent.getBooleanExtra("mediatek_read_probe", false)) {
            val pending = goAsync()
            val app = context.applicationContext
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val result = MediaTekAvReader.snapshot(app).put("cec", MediaTekAvReader.cecSnapshot(app).getJSONObject("cec")).toString()
                    java.io.File(app.getExternalFilesDir(null), "mediatek-read-probe.json").writeText(result)
                    android.util.Log.i("AerialMtkProbe", result)
                } catch (e: Exception) {
                    java.io.File(app.getExternalFilesDir(null), "mediatek-read-probe.json").writeText(
                        org.json.JSONObject().put("status", "unavailable").put("error", e.javaClass.simpleName).toString())
                    android.util.Log.e("AerialMtkProbe", "Probe unavailable", e)
                } finally { pending.finish() }
            }
            return
        }
        // ... --es export_tiles <name>   (every tile on one picture; see TileSheet)
        // Runs on past the broadcast's time limit; Home keeps the process alive. Look for the file.
        intent.getStringExtra("export_tiles")?.takeIf { Regex("^[A-Za-z0-9_-]+$").matches(it) }?.let { name ->
            val app = context.applicationContext
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { TileSheet.export(app, name) }.onFailure { android.util.Log.e("AerialTiles", "Export failed", it) }
            }
            resultData = "exporting $name"
            return
        }
        if (intent.getBooleanExtra("clear_pin", false)) {
            AerialSettings.init(context)
            AerialSettings.choosePin(null)
        }
        val layout = mapOf<String, (Boolean) -> Unit>(
            "large_clock" to AerialSettings::chooseLargeClock,
            "clock_right" to AerialSettings::chooseClockRight,
            "one_row_home" to AerialSettings::chooseOneRowHome,
            "peek_next_row" to AerialSettings::choosePeekNextRow,
            "home_plate" to AerialSettings::chooseHomePlate,
            // The name setting has three modes since the big name (#348); this switch is every name or the focused one's.
            "show_all_names" to { on: Boolean -> AerialSettings.chooseNameMode(if (on) NameMode.All else NameMode.Focused) },
        ).filterKeys { intent.hasExtra(it) }
        if (layout.isNotEmpty()) {
            AerialSettings.init(context)
            layout.forEach { (key, choose) -> choose(intent.getBooleanExtra(key, false)) }
        }
        // ... --ez diag true / --ez mode_switch false / --ez soft_layer true   (see DiagLog;
        //                                 the last two apply the next time Home starts)
        if (intent.hasExtra("diag")) DiagLog.setEnabled(context, intent.getBooleanExtra("diag", false))
        for (key in listOf("mode_switch", "soft_layer")) {
            if (intent.hasExtra(key)) DiagLog.setOption(context, key, intent.getBooleanExtra(key, false))
        }
        if (intent.hasExtra("update_test")) {
            Updates.testApk = if (intent.getBooleanExtra("update_test", false)) {
                java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "update-test.apk")
            } else null
        }
        if (intent.getBooleanExtra("check_update", false)) {
            Updates.init(context)
            Updates.checkIfStale(force = true)
        }
        if (intent.hasExtra("keep_adb")) GuardSetup.setKeepAdb(context, intent.getBooleanExtra("keep_adb", false))
        // ... --ei adb_probe_port <port>   (verify the keep_adb toggle without closing a working
        //                                 link, until Aerial restarts; issue #454)
        if (intent.hasExtra("adb_probe_port")) GuardSetup.adbProbePort = intent.getIntExtra("adb_probe_port", 5555)
        if (intent.hasExtra("guard_off_in_standby")) GuardSetup.setOffInStandby(context, intent.getBooleanExtra("guard_off_in_standby", false))
        if (intent.hasExtra("own_screensaver")) GuardSetup.setOwnScreensaver(context, intent.getBooleanExtra("own_screensaver", false))
        // aerial, photos, clock, videos or system.
        intent.getStringExtra("screensaver")?.let { key ->
            Wallpapers.init(context)
            ScreensaverMode.entries.firstOrNull { it.key == key }?.let { Wallpapers.chooseScreensaver(it) }
        }
        // Opt-in for an input ("*" = any recognized Apple TV input). Empty input disables.
        // Optional player and current-input setting key allow testing other firmware routes.
        val cecKeys = listOf("cec_home_input", "cec_home_player", "cec_home_input_setting").filter { intent.hasExtra(it) }
        if (cecKeys.isNotEmpty()) {
            val edit = context.getSharedPreferences("system", Context.MODE_PRIVATE).edit()
            for (key in cecKeys) edit.putString(key, intent.getStringExtra(key)?.takeIf { it.isNotBlank() })
            edit.commit()
            HomeGuardService.refreshCecHome()
        }
        GuardSetup.ensureEnabled(context)
        resultData = "keep_adb=${GuardSetup.keepAdb(context)} guard_off_in_standby=${GuardSetup.offInStandby(context)}"
    }
}
