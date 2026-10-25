package app.orchard.tv.ui.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.system.GaiaAvSettings
import app.orchard.tv.ui.control.SliderRow
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** No generic screen_brightness fallback: this value is the actual CVTE backlight. */
@Composable
internal fun GaiaBacklightRow(vm: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.snapshot(context) }.getOrNull() }
        busy = false
    }
    val backlight = data?.optJSONObject("backlight")
    if (backlight == null || backlight.optString("status") != "ITEM_STATUS_ENABLE") {
        SettingRow(Glyph.Sun, stringResource(R.string.gs_av_backlight),
            detail = stringResource(if (busy) R.string.tasks_loading else R.string.unavailable)) {
            vm.whenUnlocked { vm.overlay = Overlay.GaiaSettings("picture") }
        }
    } else {
        val current = backlight.getInt("value")
        SliderRow(Glyph.Sun, current / 100f, "$current%", 20, onStep = { direction ->
            val expected = data
            if (!busy && expected != null) vm.whenUnlocked {
                busy = true
                scope.launch {
                    failed = withContext(Dispatchers.IO) { runCatching {
                        GaiaAvSettings.change(context, "backlight", (current + direction * 5).coerceIn(5, 100).toString(), expected)
                    }.isFailure }
                    data = withContext(Dispatchers.IO) { runCatching { GaiaAvSettings.snapshot(context) }.getOrNull() }
                    busy = false
                }
            }
        }, onClick = { vm.whenUnlocked { vm.overlay = Overlay.GaiaSettings("picture") } })
    }
    if (failed) BasicText(stringResource(R.string.gs_write_failed), style = Type.Caption)
}
