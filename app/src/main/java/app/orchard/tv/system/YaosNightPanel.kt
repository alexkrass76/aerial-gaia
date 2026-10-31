package app.orchard.tv.system

import android.content.Context
import app.orchard.tv.tasks.LocalAdbTransport
import org.json.JSONObject

/** Stock TvSettingsPlus ContentObserver route, physically confirmed on the YaOS TV.
 * Shell native setters can update readback without visible panel dimming on this firmware.
 * Never create an absent Global key or guess an auto-backlight mode.
 */
internal object YaosNightPanel {
    private const val KEY = "g_disp__disp_back_light"
    fun snapshot(context: Context): JSONObject {
        val root = MediaTekAvReader.snapshot(context)
        val stored = LocalAdbTransport.connect(8_000).use { it.shell("/system/bin/settings get global picture_backlight") }.trim()
        val value = stored.toIntOrNull()
        check(value != null && value in 5..100) { "Stock backlight value unavailable" }
        val row = aerial.probe.MediaTekWritePolicy.item(root,KEY)
        root.put("nightNativeBacklight",row.getInt("raw")).put("nightStoredBacklight",value)
        // Policy/ownership use the stock service's original setting, not stale native cache.
        row.put("raw",value)
        return root
    }
    fun write(context: Context, target: Int, expected: JSONObject): JSONObject {
        require(target in 5..100)
        val before = snapshot(context)
        aerial.probe.MediaTekWritePolicy.validate(before,expected,KEY,target)
        check(aerial.probe.MediaTekWritePolicy.value(before,"g_disp__disp_adp_back_light") == 0) { "Adaptive backlight active" }
        val old = before.getInt("nightStoredBacklight")
        val command = "if [ \"\$(/system/bin/settings get global picture_backlight)\" = '$old' ]; then /system/bin/settings put global picture_backlight $target && echo AERIAL_PANEL_OK; fi"
        check(LocalAdbTransport.connect(8_000).use { it.shell(command) }.trim() == "AERIAL_PANEL_OK")
        val deadline = System.nanoTime()+8_000_000_000L
        do {
            val after = snapshot(context)
            aerial.probe.MediaTekWritePolicy.context(after,before)
            if(after.getInt("nightStoredBacklight")==target && after.getInt("nightNativeBacklight")==target) return after
            Thread.sleep(250)
        } while(System.nanoTime()<deadline)
        error("Stock service did not apply backlight")
    }
}
