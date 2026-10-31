package app.orchard.tv.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.theme.Motion
import kotlinx.coroutines.delay

/** Pure monotonic envelope, independent of video changes, wall time and weather updates. */
internal object NightModeEnvelope {
    val minutes = listOf(5, 15, 30, 60)
    val dimming = listOf(50, 75, 90)
    fun level(elapsedMs: Long, durationMs: Long, percent: Int): Float {
        val t = (elapsedMs.coerceAtLeast(0).toDouble() / durationMs.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return (t * t * (3 - 2 * t) * percent.coerceIn(0, 100) / 100.0).toFloat()
    }
}

/** One solid composition layer above SurfaceView AND information; no frame capture/shader/bitmap.
 * Samples only four times a second during the long fade, then sleeps indefinitely.
 * State is read in draw, keeping video and the rest of Home out of these updates.
 * Native panel dimming is handled separately by NightBacklight.
 */
@Composable
internal fun NightModeVeil(visible: Boolean) {
    var level by remember { mutableFloatStateOf(0f) }
    val enabled = AerialSettings.screensaverNight
    val minutes = AerialSettings.screensaverNightMinutes
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(visible, enabled, minutes) {
        if (visible && enabled) app.orchard.tv.system.NightBacklight.session(context, minutes)
        else app.orchard.tv.system.NightBacklight.recover(context)
    }
    LaunchedEffect(visible, enabled, minutes) {
        if (visible && enabled) {
            val start = SystemClock.elapsedRealtime()
            val from = level
            val duration = minutes * 60_000L
            val target = 0.30f
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - start
                val fraction = NightModeEnvelope.level(elapsed, duration, 100)
                level = from + (target - from) * fraction
                if (elapsed >= duration) { level = target; break }
                delay(250)
            }
        } else if (level > 0f) {
            // Short frame-paced return prevents a bright flash when Back returns to Home.
            Animatable(level).animateTo(0f, tween(1500, easing = Motion.EaseInOut)) { level = value }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (level > 0f) drawRect(Color(0xFFFF9A40), alpha = level)
    }
}
