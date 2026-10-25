package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.*
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import app.orchard.tv.tasks.TaskBridgeClient

/** Opt-in passive volume UI. No window focus, touch interception or vendor package changes. */
internal class GaiaVolumeOverlay(private val service: AccessibilityService, private val changed: () -> Unit) {
    companion object {
        fun preferences(context: Context): SharedPreferences = context.createDeviceProtectedStorageContext().getSharedPreferences("gaia_volume_overlay", 0)
        fun enabled(context: Context) = context.packageName == "app.orchard.tv.gaia" && preferences(context).getBoolean("enabled", false)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)
    private val windows = service.getSystemService(WindowManager::class.java)
    private val prefs = preferences(service)
    private val events = GaiaVolumeEvents(service, { onVolumeChanged() }, { windowFailed = true; releaseLease() })
    private var view: Meter? = null
    private val dismiss = Runnable { hide() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var leaseJob: Job? = null
    private var leased = false
    private var windowFailed = false
    private var windowVerified = false
    private fun updateLease() {
        leaseJob?.cancel()
        leaseJob = scope.launch {
            while (isActive) {
                val requested = enabled(service) && prefs.getBoolean("replace_stock", false)
                val healthy = requested && events.ready && windowVerified && !windowFailed &&
                    service.getSystemService(android.os.PowerManager::class.java).isInteractive
                if (healthy) {
                    // Heartbeats must never create visible windows. Require a successful real display.
                    runCatching { withContext(Dispatchers.IO) { TaskBridgeClient.request(14) } }
                        .onSuccess { leased = true }
                } else if (leased) {
                    runCatching { withContext(Dispatchers.IO) { TaskBridgeClient.request(15) } }
                    leased = false
                }
                if (!requested) break
                delay(3000)
            }
        }
    }
    private fun releaseLease() {
        leaseJob?.cancel(); leaseJob = null
        if (leased) scope.launch(Dispatchers.IO) { runCatching { TaskBridgeClient.request(15) } }
        leased = false
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { if (enabled(service)) events.start() else { events.close(); hide() }; windowFailed = false; updateLease(); changed() } }
    fun start() { prefs.registerOnSharedPreferenceChangeListener(listener); if (enabled(service)) events.start(); updateLease() }
    fun close() { releaseLease(); prefs.unregisterOnSharedPreferenceChangeListener(listener); handler.removeCallbacksAndMessages(null); events.close(); hide() }
    fun hide() { view?.let { runCatching { windows.removeView(it) } }; view = null }
    private fun onVolumeChanged() {
        if (!enabled(service) || !service.getSystemService(android.os.PowerManager::class.java).isInteractive) return
        // This prototype is for speaker tests; external route semantics remain unverified.
        if (audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }) return
        if (show()) {
            refresh()
            handler.removeCallbacks(dismiss)
            handler.postDelayed(dismiss, 1800)
        }
    }
    private fun show(): Boolean = try {
        if (view == null) {
            val meter = Meter(service).apply { fraction = currentFraction() }
            val d = service.resources.displayMetrics.density
            val params = WindowManager.LayoutParams((64*d).toInt(), (224*d).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; x = (28*d).toInt(); title = "Aerial Gaia Volume" }
            windows.addView(meter, params)
            view = meter
            windowVerified = true
        }
        true
    } catch (_: RuntimeException) { windowFailed = true; false }
    private fun currentFraction(): Float {
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0f else level.toFloat()/max
    }
    private fun refresh() {
        view?.apply { fraction = currentFraction(); invalidate() }
    }
    private class Meter(context: Context) : View(context) {
        var fraction = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            canvas.save(); canvas.scale(d, d)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(220, 35, 35, 39)
            val pill = RectF(6f, 32f, 58f, 218f)
            canvas.drawRoundRect(pill, 26f, 26f, paint)
            val clip = Path().apply { addRoundRect(pill, 26f, 26f, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(clip)
            paint.color = Color.argb(245, 245, 245, 248)
            canvas.drawRect(6f, 218f - 186f * fraction.coerceIn(0f, 1f), 58f, 218f, paint)
            canvas.restore()
            paint.color = Color.WHITE; paint.textSize = 18f; paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            canvas.drawText("${(fraction*100).roundToInt()}%", 32f, 23f, paint)
            paint.color = if (fraction > 0.18f) Color.rgb(35,35,39) else Color.WHITE
            val speaker = Path().apply { moveTo(20f,190f); lineTo(25f,190f); lineTo(32f,184f); lineTo(32f,204f); lineTo(25f,198f); lineTo(20f,198f); close() }
            canvas.drawPath(speaker,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.strokeCap = Paint.Cap.ROUND
            if (fraction == 0f) { canvas.drawLine(37f,190f,44f,198f,paint); canvas.drawLine(44f,190f,37f,198f,paint) }
            else canvas.drawArc(RectF(31f,185f,44f,203f), -65f,130f,false,paint)
            canvas.restore()
        }
    }
}
