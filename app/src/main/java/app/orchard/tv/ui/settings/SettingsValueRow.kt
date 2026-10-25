package app.orchard.tv.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.theme.*

/** Focusable for remote scrolling; intentionally has no click action. */
@Composable
internal fun SettingsValueRow(glyph: Glyph, label: String, value: String) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Palette.OnFocus else Palette.Label
    val shift by animateDpAsState(if (focused && !LocalReducedMotion.current) 8.dp else 0.dp,
        spring(dampingRatio = 1f, stiffness = 520f), label = "settings-value-shift")
    Row(Modifier.fillMaxWidth().padding(end = 10.dp).offset(x = shift)
        .onFocusChanged { focused = it.isFocused }.focusable().heightIn(min = 48.dp)
        .background(if (focused) Palette.Focus else Color.Transparent, RoundedCornerShape(12.dp))
        .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(foreground.copy(alpha = 0.08f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, foreground, 21.dp)
        }
        Spacer(Modifier.width(14.dp))
        BasicText(label, style = Type.Body.copy(color = foreground), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(20.dp))
        BasicText(value, style = Type.Body.copy(color = foreground.copy(alpha = 0.7f), textAlign = TextAlign.End), modifier = Modifier.weight(1f))
    }
}
