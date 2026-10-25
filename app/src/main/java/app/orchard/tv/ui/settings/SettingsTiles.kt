package app.orchard.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.*

private data class SettingsSection(val id: String, val glyph: Glyph, val title: Int, val hint: Int?)
private val sections = listOf(
    SettingsSection("personalize", Glyph.Photo, R.string.cc_personalize, null),
    SettingsSection("network", Glyph.Wifi, R.string.cc_network, R.string.gs_network_detail),
    SettingsSection("bluetooth", Glyph.Bluetooth, R.string.cc_bluetooth, R.string.gs_bluetooth_detail),
    SettingsSection("picture", Glyph.Sun, R.string.gs_av_picture, null),
    SettingsSection("sound", Glyph.Speaker, R.string.gs_sound, R.string.gs_sound_detail),
    SettingsSection("apps", Glyph.Bag, R.string.gs_apps, R.string.gs_apps_detail),
    SettingsSection("advanced", Glyph.Gear, R.string.gs_preferences, R.string.gs_preferences_detail),
    SettingsSection("system", Glyph.Info, R.string.gs_system, R.string.gs_system_detail),
)

/** Three remote-friendly columns on the launcher's 960 dp canvas. */
@Composable
internal fun SettingsTiles(selected: String?, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val index = sections.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val scroll = rememberLazyGridState(initialFirstVisibleItemIndex = index / 3 * 3)
    val focus = remember(selected) { FocusRequester() }
    LaunchedEffect(selected) {
        scroll.scrollToItem(index)
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3), state = scroll,
        modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(sections, key = { _, section -> section.id }) { i, section ->
            Pressable(onClick = { onOpen(section.id) }, focusRequester = focus.takeIf { i == index },
                focusedScale = 1.025f, modifier = Modifier.fillMaxWidth()) { state ->
                val foreground = if (state.focused) Palette.OnFocus else Palette.Label
                Row(Modifier.fillMaxWidth().height(104.dp)
                    .background(if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(section.glyph, foreground, 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(stringResource(section.title), style = Type.Body.copy(color = foreground),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        section.hint?.let {
                            BasicText(stringResource(it), style = Type.Caption.copy(color = foreground.copy(alpha = 0.7f)),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
