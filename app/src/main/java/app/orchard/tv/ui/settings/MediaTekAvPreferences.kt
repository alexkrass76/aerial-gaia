package app.orchard.tv.ui.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.orchard.tv.R
import app.orchard.tv.system.MediaTekAvReader
import app.orchard.tv.system.YaosCecControl
import app.orchard.tv.system.MediaTekReadValue
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.launch
import app.orchard.tv.HomeViewModel
import app.orchard.tv.ui.control.SliderRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable
internal fun MediaTekAvPreferences(picture: Boolean, revision: Int, cec: Boolean, speakers: Boolean, vm: HomeViewModel) {
    val context = LocalContext.current
    if (cec) { YaosCecPreferences(revision, vm); return }
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf(MediaTekAvReader.hasUndo(context)) }
    var writeFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(revision, refresh, cec) {
        busy = true
        try {
            data = withContext(Dispatchers.IO) { if (cec) MediaTekAvReader.cecSnapshot(context) else MediaTekAvReader.snapshot(context) }
            failed = false
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { data = null; failed = true }
        finally { busy = false }
    }
    fun write(key: String?, target: Int = 0) {
        if (busy) return
        val expected = data ?: return
        vm.whenUnlocked {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (key == null) MediaTekAvReader.undo(context)
                        else MediaTekAvReader.change(context, key, target, expected)
                    }
                    writeFailed = false
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { writeFailed = true }
                finally {
                    undo = MediaTekAvReader.hasUndo(context)
                    busy = false
                    refresh++
                }
            }
        }
    }
    if (undo) SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_av_undo)) { write(null) }
    if (writeFailed) BasicText(stringResource(R.string.gs_mtk_write_failed), style = Type.Caption)
    SettingRow(Glyph.Gear, stringResource(R.string.gs_refresh)) { if (!busy) refresh++ }
    if (!cec) BasicText(stringResource(if (picture || speakers) R.string.gs_mtk_verified_write else R.string.gs_mtk_readonly), style = Type.Caption)
    if (busy) BasicText(stringResource(R.string.tasks_loading), style = Type.Body)
    if (failed) BasicText(stringResource(R.string.gs_mtk_unavailable), style = Type.Caption)
    if (cec) {
        val state = data?.optJSONObject("cec")
        for ((key, label) in listOf("enabled" to R.string.gs_cec_title,
                "auto_off" to R.string.gs_cec_device_off, "auto_wake" to R.string.gs_cec_wakeup,
                "volume" to R.string.gs_cec_volume, "system_audio" to R.string.gs_cec_system_audio,
                "arc_established" to R.string.gs_cec_arc_established)) {
            val known = state?.opt(key) as? Boolean
            SettingsValueRow(Glyph.Info, stringResource(label), stringResource(
                if (known == null) R.string.gs_unknown else if (known) R.string.st_on else R.string.st_off))
        }
        val ports = state?.optJSONArray("arc_ports")
        SettingsValueRow(Glyph.Info, stringResource(R.string.gs_cec_arc_ports),
            if (ports == null) stringResource(R.string.gs_unknown)
            else if (ports.length() == 0) "—" else (0 until ports.length()).joinToString(", ") { "HDMI " + ports.getInt(it) })
        BasicText(stringResource(R.string.gs_cec_snapshot_hint), style = Type.Caption)
        return
    }
    val keys = when {
        cec -> listOf("g_cec__cec_func" to R.string.gs_cec_title,
            "g_cec__cec_auto_off" to R.string.gs_cec_device_off, "g_cec__cec_auto_on" to R.string.gs_cec_wakeup)
        speakers -> listOf("g_audio__aud_balance" to R.string.gs_speaker_balance)
        picture -> listOf("g_video__brightness" to R.string.gs_av_brightness,
            "g_video__contrast" to R.string.gs_av_contrast, "g_video__vid_sat" to R.string.gs_av_color,
            "g_video__vid_shp" to R.string.gs_av_sharpness, "g_video__vid_hue" to R.string.gs_av_tint,
            "g_disp__disp_back_light" to R.string.gs_av_backlight)
        else -> listOf("g_audio__aud_out_port" to R.string.gs_av_outputs,
            "g_audio__spdif" to R.string.gs_av_digital)
    }
    val items = data?.optJSONArray("items")
    val rows = (0 until (items?.length() ?: 0)).associate {
        val item = items!!.getJSONObject(it); item.optString("key") to item
    }
    if (!failed) for ((key, label) in keys) {
        val value = rows[key]?.let(MediaTekReadValue::scalar)
        // Only verified continuous scalars have a user-facing interpretation.
        val scalar = key in setOf("g_video__brightness", "g_video__contrast", "g_video__vid_sat",
            "g_video__vid_shp", "g_video__vid_hue", "g_audio__aud_balance", "g_disp__disp_back_light")
        if (scalar && value != null && (key != "g_disp__disp_back_light" || (rows[key]?.optInt("visible",-1) == 0 && rows[key]?.optInt("enabled",-1) == 0 && value >= 5))) {
            val low = if (speakers) -50 else if (key == "g_disp__disp_back_light") 5 else 0
            val high = if (speakers) 50 else 100
            SliderRow(if (speakers) Glyph.Speaker else Glyph.Sun, (value - low) / (high - low).toFloat(),
                (if (key == "g_disp__disp_back_light") "$value%" else value.toString()), 20, title = stringResource(label),
                onStep = { direction -> if (!busy) write(key, (value + direction * 5).coerceIn(low, high)) }, onClick = {})
        } else if (key == "g_disp__disp_back_light") {
            val item = rows[key]
            val supported = item?.optInt("visible", -1) == 0 && item.optInt("enabled", -1) == 0
            SettingsValueRow(Glyph.Sun, stringResource(label), if (supported && value != null) "$value%" else stringResource(R.string.gs_unknown))
            BasicText(stringResource(R.string.gs_mtk_readonly), style = Type.Caption)
        } else SettingsValueRow(Glyph.Info, stringResource(label), stringResource(R.string.gs_mtk_unverified))
    }
}
