package app.orchard.tv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.system.GaiaAvPolicy
import app.orchard.tv.ui.control.SliderRow
import kotlinx.coroutines.launch
import app.orchard.tv.system.GaiaAvSettings
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun AvPreferences(picture: Boolean, revision: Int, vm: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var undo by remember { mutableStateOf(GaiaAvSettings.hasUndo(context)) }
    var edit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(revision, refresh) {
        busy = true
        val result = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.snapshot(context) } }
        data = result.getOrNull()
        failed = result.isFailure
        busy = false
    }
    fun apply(key: String, value: String) {
        if (busy) return
        val expected = data ?: return
        vm.whenUnlocked {
            busy = true
            scope.launch {
                failed = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.change(context, key, value, expected) }.isFailure }
                undo = GaiaAvSettings.hasUndo(context)
                edit = null
                // Refresh real values even after a refusal or a partial firmware failure.
                val actual = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.snapshot(context) }.getOrNull() }
                data = actual
                busy = false
            }
        }
    }
    if (failed) BasicText(stringResource(R.string.gs_write_failed), style = Type.Caption)
    if (undo) SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_av_undo)) {
        if (!busy) vm.whenUnlocked {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.undo(context) } }
                failed = result.isFailure
                undo = GaiaAvSettings.hasUndo(context)
                data = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.snapshot(context) }.getOrNull() }
                busy = false
            }
        }
    }
    edit?.let { (key, value) ->
        androidx.activity.compose.BackHandler { edit = null }
        BasicText(stringResource(R.string.gs_av_confirm, avName(value)), style = Type.Body)
        SettingRow(Glyph.Check, stringResource(R.string.gs_av_apply)) { if (!busy) apply(key, value) }
        SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back)) { edit = null }
        if (busy) BasicText(stringResource(R.string.tasks_loading), style = Type.Caption)
        return
    }
    SettingRow(Glyph.Gear, stringResource(R.string.gs_refresh)) { if (!busy) refresh++ }
    if (busy || failed) BasicText(stringResource(if (busy) R.string.tasks_loading else R.string.unavailable), style = Type.Body)
    data?.let { root ->
        if (picture) {
            root.optJSONObject("picture")?.let { current ->
                AvValue(stringResource(R.string.gs_av_mode), avName(current.optString("mode")))
                AvModes(current) { mode ->
                    if (!busy && mode in GaiaAvPolicy.pictureModes && mode != current.optString("mode")) edit = "picture_mode" to mode
                }
                val items = current.optJSONArray("items")
                for (i in 0 until (items?.length() ?: 0)) {
                    val item = items!!.getJSONObject(i)
                    val label = when (item.optString("key")) {
                        "PICTURE_BRIGHTNESS" -> R.string.gs_av_brightness
                        "PICTURE_CONTRAST" -> R.string.gs_av_contrast
                        "PICTURE_COLOR" -> R.string.gs_av_color
                        "PICTURE_SHARPNESS" -> R.string.gs_av_sharpness
                        "PICTURE_TINT" -> R.string.gs_av_tint
                        else -> continue
                    }
                    if (item.optString("status") == "ITEM_STATUS_ENABLE" && current.optString("mode") == "OPTION_PICTURE_MODE_USER") {
                        BasicText(stringResource(label), style = Type.Caption)
                        SliderRow(Glyph.Sun, item.getInt("value") / 100f, item.getInt("value").toString(), 20,
                            onStep = { direction -> if (!busy) apply(item.getString("key"), (item.getInt("value") + direction * 5).coerceIn(0, 100).toString()) }, onClick = {})
                    } else AvValue(stringResource(label), item.getInt("value").toString())
                    if (item.optString("status") != "ITEM_STATUS_ENABLE")
                        BasicText(stringResource(R.string.gs_av_locked), style = Type.Caption)
                }
            } ?: BasicText(stringResource(R.string.unavailable), style = Type.Body)
            root.optJSONObject("backlight")?.let {
                BasicText(stringResource(R.string.gs_av_backlight), style = Type.Caption)
                SliderRow(Glyph.Sun, it.getInt("value") / 100f, "${it.getInt("value")}%", 20,
                    onStep = { direction -> if (!busy) apply("backlight", (it.getInt("value") + direction * 5).coerceIn(5, 100).toString()) }, onClick = {})
                if (it.optString("status") != "ITEM_STATUS_ENABLE")
                    BasicText(stringResource(R.string.gs_av_locked), style = Type.Caption)
            }
        } else {
            for ((group, label) in listOf("sound" to R.string.gs_av_outputs, "digital" to R.string.gs_av_digital)) {
                root.optJSONObject(group)?.let {
                    AvValue(stringResource(label), avName(it.optString("mode")))
                    if (group == "digital") AvModes(it) { mode ->
                        if (!busy && mode in setOf("SPDIF_OUT_PCM", "SPDIF_OUT_RAW") && mode != it.optString("mode")) edit = "digital" to mode
                    } else {
                        AvModes(it)
                        BasicText(stringResource(R.string.gs_av_routing_pending), style = Type.Caption)
                    }
                } ?: AvValue(stringResource(label), stringResource(R.string.unavailable))
            }
        }
    }
}

@Composable
private fun AvModes(data: JSONObject, choose: ((String) -> Unit)? = null) {
    val modes = data.optJSONArray("modes")
    if (choose == null) {
        val labels = (0 until (modes?.length() ?: 0)).map { avName(modes!!.getString(it)) }
        AvValue(stringResource(R.string.gs_av_available), labels.joinToString("\n"))
    } else for (i in 0 until (modes?.length() ?: 0)) {
        val mode = modes!!.getString(i)
        SettingRow(Glyph.Gear, avName(mode), detail = if (data.optString("mode") == mode) stringResource(R.string.gs_selected) else null) { choose(mode) }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AvValue(label: String, value: String) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Palette.OnFocus else Palette.Label
    Row(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
        .background(if (focused) Palette.Focus else Palette.Fill, RoundedCornerShape(14.dp)).padding(12.dp)) {
        BasicText(label, style = Type.Body.copy(color = foreground), modifier = Modifier.weight(1f))
        BasicText(value, style = Type.Body.copy(color = foreground), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun avName(value: String): String = when (value) {
    "OPTION_PICTURE_MODE_STANDARD" -> stringResource(R.string.gs_av_standard)
    "OPTION_PICTURE_MODE_DYNAMIC" -> stringResource(R.string.gs_av_dynamic)
    "OPTION_PICTURE_MODE_SOFT" -> stringResource(R.string.gs_av_soft)
    "OPTION_PICTURE_MODE_ENERGY_SAVING" -> stringResource(R.string.gs_av_energy)
    "OPTION_PICTURE_MODE_USER" -> stringResource(R.string.gs_av_user)
    "OPTION_SOUND_OUT_SPEAKER" -> stringResource(R.string.gs_av_speakers)
    "OPTION_SOUND_OUT_HEADPHONES" -> stringResource(R.string.gs_av_headphones)
    "OPTION_SOUND_OUT_LINEOUT" -> stringResource(R.string.gs_av_lineout)
    "OPTION_SOUND_OUT_BLUETOOTH" -> "Bluetooth"
    "OPTION_SOUND_OUT_SPDIF" -> "S/PDIF"
    "OPTION_SOUND_OUT_ARC" -> "HDMI ARC"
    "SPDIF_OUT_PCM" -> "PCM"
    "SPDIF_OUT_RAW" -> "RAW"
    else -> stringResource(R.string.unavailable)
}
