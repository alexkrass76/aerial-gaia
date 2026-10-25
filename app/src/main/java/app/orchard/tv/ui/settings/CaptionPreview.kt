package app.orchard.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/** Illustrates the built-in presets; actual rendering belongs to each player. */
@Composable
internal fun CaptionPreview(preset: String?, scale: String?) {
    BasicText(stringResource(R.string.gs_captions_preview), style = Type.Caption)
    val colors = when (preset) {
        "0", null -> Color.White to Color.Black
        "1" -> Color.Black to Color.White
        "2" -> Color.Yellow to Color.Black
        "3" -> Color.Yellow to Color.Blue
        else -> null // Custom and unspecified styles are resolved by the individual player.
    }
    Box(Modifier.fillMaxWidth().height(90.dp).background(Palette.Fill), contentAlignment = Alignment.Center) {
        if (colors == null) BasicText(stringResource(R.string.gs_captions_player_style), style = Type.Caption)
        else BasicText(stringResource(R.string.gs_captions_sample),
            style = Type.Body.copy(color = colors.first, fontSize = (20f * (scale?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 1f).coerceIn(0.5f, 2f)).sp),
            modifier = Modifier.background(colors.second).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
