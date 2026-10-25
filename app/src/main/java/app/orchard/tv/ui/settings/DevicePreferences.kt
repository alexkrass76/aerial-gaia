package app.orchard.tv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.system.GaiaDeviceSettings
import app.orchard.tv.system.GaiaDeviceSettings.Key
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All controls stay in Aerial; failures refresh actual state rather than optimistic toggles. */
@Composable
internal fun DevicePreferences(page: String, vm: HomeViewModel, revision: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<Map<Key, String?>>(emptyMap()) }
    var keyboards by remember { mutableStateOf<List<GaiaDeviceSettings.Keyboard>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision, refresh) {
        val result = withContext(Dispatchers.IO) { runCatching {
            Triple(Key.entries.associateWith { GaiaDeviceSettings.read(context, it) },
                GaiaDeviceSettings.keyboards(context), GaiaDeviceSettings.selectedKeyboard(context))
        } }
        result.onSuccess { values = it.first; keyboards = it.second; selected = it.third }
        if (result.isFailure) failed = true
    }
    fun change(operation: () -> Unit) {
        if (busy) return
        vm.whenUnlocked {
            busy = true
            scope.launch {
                failed = withContext(Dispatchers.IO) { runCatching { operation() }.isFailure }
                refresh++
                busy = false
            }
        }
    }
    if (failed) BasicText(stringResource(R.string.gs_write_failed), style = Type.Caption)
    if (busy) BasicText(stringResource(R.string.tasks_loading), style = Type.Caption)
    val rows = when (page) {
        "captions" -> listOf(Key.CAPTIONS_ENABLED to R.string.gs_captions_enabled,
            Key.CAPTIONS_SCALE to R.string.gs_captions_size, Key.CAPTIONS_STYLE to R.string.gs_captions_style)
        "datetime" -> listOf(Key.AUTO_TIME to R.string.gs_auto_time, Key.CLOCK_FORMAT to R.string.gs_clock_format)
        "display" -> listOf(Key.FONT_SCALE to R.string.gs_font_scale,
            Key.WINDOW_ANIMATION to R.string.gs_window_animation,
            Key.TRANSITION_ANIMATION to R.string.gs_transition_animation,
            Key.ANIMATOR_DURATION to R.string.gs_animator_duration)
        "sound" -> listOf(Key.TOUCH_SOUND to R.string.gs_touch_sound)
        else -> emptyList()
    }
    if (page == "captions") {
        BasicText(stringResource(R.string.gs_captions_scope), style = Type.Caption)
        CaptionPreview(values[Key.CAPTIONS_STYLE], values[Key.CAPTIONS_SCALE])
    }
    if (page == "datetime") {
        BasicText(java.util.TimeZone.getDefault().id, style = Type.Body)
        BasicText(stringResource(R.string.gs_time_scope), style = Type.Caption)
    }
    if (page == "keyboard") {
        BasicText(stringResource(R.string.gs_keyboard_scope), style = Type.Caption)
        keyboards.forEach { keyboard ->
            SettingRow(Glyph.Gear, keyboard.label, detail = stringResource(if (selected == keyboard.id) R.string.gs_selected else R.string.gs_select)) {
                val expected = selected
                change { GaiaDeviceSettings.selectKeyboard(context, keyboard.id, expected) }
            }
        }
    }
    rows.forEach { (key, title) ->
        val current = values[key]
        val text = when {
            current == null -> stringResource(if (values.containsKey(key)) R.string.gs_default_value else R.string.tasks_loading)
            key == Key.AUTO_TIME || key == Key.TOUCH_SOUND || key == Key.CAPTIONS_ENABLED -> stringResource(if (current == "1") R.string.perm_on else R.string.perm_off)
            key == Key.CAPTIONS_STYLE -> stringResource(when (current) {
                "0" -> R.string.gs_captions_white_black
                "1" -> R.string.gs_captions_black_white
                "2" -> R.string.gs_captions_yellow_black
                "3" -> R.string.gs_captions_yellow_blue
                "4" -> R.string.gs_default_value
                else -> R.string.gs_captions_custom
            })
            key == Key.CLOCK_FORMAT -> stringResource(R.string.gs_hours, current)
            else -> "$current ×"
        }
        SettingRow(Glyph.Gear, stringResource(title), detail = text) {
            if (values.containsKey(key)) {
                val index = key.options.indexOfFirst { GaiaDeviceSettings.equivalent(it, current) }
                change { GaiaDeviceSettings.set(context, key, current, key.options[(index + 1) % key.options.size]) }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
