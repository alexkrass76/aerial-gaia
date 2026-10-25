package app.orchard.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.theme.*

/** Decorative section identity; excluded from the remote focus chain. */
@Composable
internal fun SettingsSectionArtwork(page: String, title: String, modifier: Modifier = Modifier) {
    val glyph = when (page) {
        "network" -> Glyph.Wifi
        "bluetooth" -> Glyph.Bluetooth
        "picture", "display" -> Glyph.Sun
        "sound", "outputs", "speakers" -> Glyph.Speaker
        "cec" -> Glyph.Hdmi
        "apps" -> Glyph.Bag
        "datetime" -> Glyph.Calendar
        "system" -> Glyph.Info
        else -> Glyph.Gear
    }
    Column(modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(180.dp).background(Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.09f))), RoundedCornerShape(42.dp)),
            contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, Palette.Label, 88.dp)
        }
        Spacer(Modifier.height(24.dp))
        BasicText(title, style = Type.Headline.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    }
}
