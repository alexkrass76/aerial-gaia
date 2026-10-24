package app.orchard.tv.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.weather.Weather
import java.time.LocalDateTime

/** Unboxed information over video; text shadows preserve contrast without capturing the video plane. */
@Composable
internal fun SaverInformationCard(now: LocalDateTime) {
    val textShadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f), 6f)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(if (AerialSettings.clockRight) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = 48.dp, vertical = 36.dp)
                .width(420.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            BasicText(dateText(now), style = Type.Headline.copy(color = Color.White.copy(alpha = 0.9f), shadow = textShadow))
            BasicText(timeText(now), style = Type.Clock.copy(fontSize = 64.sp, color = Color.White, shadow = textShadow))
            Weather.now?.let { weather ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(glyphFor(weather), Color.White, 22.dp, Modifier.padding(end = 8.dp))
                    BasicText("${weather.celsius}°", style = Type.Title.copy(color = Color.White, shadow = textShadow))
                    val details = listOfNotNull(Weather.place, stringResource(conditionName(weather.kind))).joinToString(" · ")
                    BasicText(details, style = Type.Body.copy(color = Color.White.copy(alpha = 0.9f), shadow = textShadow),
                        modifier = Modifier.padding(start = 10.dp), maxLines = 2)
                }
                if (weather.high != null && weather.low != null) BasicText(
                    stringResource(R.string.weather_high_low, weather.high, weather.low),
                    style = Type.Body.copy(color = Color.White.copy(alpha = 0.75f), shadow = textShadow),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
