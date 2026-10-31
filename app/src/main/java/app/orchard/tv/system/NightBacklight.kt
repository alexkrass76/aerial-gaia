package app.orchard.tv.system

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.orchard.tv.ui.home.NightModeEnvelope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

internal object NightBacklightPolicy {
    const val TARGET = 5
    fun target(original: Int, progress: Float): Int =
        (original + (TARGET - original) * progress.coerceIn(0f, 1f)).roundToInt().coerceIn(5, 100)
    fun owned(actual: Int, record: JSONObject): Boolean = actual == record.getInt("last") || actual == record.getInt("next")
}

/** Only the native backlight; no brightness, colour temperature, CEC or power writes.
 * Each write has a durable intent, expected context/value and hardware readback.
 * Session serialization includes non-cancellable restore; process death leaves the journal for next entry.
 */
internal object NightBacklight {
    private val mutex = Mutex()
    var failed by mutableStateOf(false); private set
    private fun prefs(c: Context) = c.getSharedPreferences("night_backlight", 0)
    private fun save(c: Context, record: JSONObject) { check(prefs(c).edit().putString("restore", record.toString()).commit()) }
    private fun mtk(c: Context) = MediaTekAvReader.available(c)
    private fun snapshot(c: Context) = if (mtk(c)) YaosNightPanel.snapshot(c) else GaiaAvSettings.snapshot(c)
    private fun value(c: Context, s: JSONObject) = if (mtk(c)) aerial.probe.MediaTekWritePolicy.value(s,"g_disp__disp_back_light") else s.getJSONObject("backlight").getInt("value")
    private fun validate(c: Context, actual: JSONObject, expected: JSONObject, target: Int) {
        if (mtk(c)) aerial.probe.MediaTekWritePolicy.validate(actual, expected,"g_disp__disp_back_light",target)
        else GaiaAvPolicy.validate(actual,expected,"backlight",target.toString(),restoring=true)
    }
    private fun write(c: Context, target: Int, expected: JSONObject) =
        if (mtk(c)) YaosNightPanel.write(c,target,expected) else GaiaAvSettings.nightBacklight(c,target,expected)

    private fun restore(c: Context, gradual: Boolean = false) {
        val text = prefs(c).getString("restore",null) ?: return
        val r = JSONObject(text)
        check(r.getBoolean("mtk") == mtk(c))
        // Migrate .52's native-only journal without issuing a new stock-service write.
        if (mtk(c) && !r.getJSONObject("snapshot").has("nightStoredBacklight")) {
            val current = MediaTekAvReader.snapshot(c)
            val actual = value(c,current)
            check(NightBacklightPolicy.owned(actual,r))
            aerial.probe.MediaTekWritePolicy.context(current,r.getJSONObject("snapshot"))
            if (actual != r.getInt("original")) MediaTekAvReader.nightBacklight(c,r.getInt("original"),current)
            check(prefs(c).edit().remove("restore").commit())
            return
        }
        val current = snapshot(c)
        val actual = value(c,current)
        check(NightBacklightPolicy.owned(actual,r)) { "Backlight changed externally" }
        val expected = r.getJSONObject("snapshot")
        // Validate the original context but use the actual owned value (write may have timed out).
        if (mtk(c)) aerial.probe.MediaTekWritePolicy.item(expected,"g_disp__disp_back_light").put("raw",actual)
        else expected.getJSONObject("backlight").put("value",actual)
        val old = r.getInt("original")
        validate(c,current,expected,old)
        var state = current
        var last = actual
        while (last != old) {
            val next = if (gradual) last + (old-last).coerceIn(-3,3) else old
            r.put("last",last).put("next",next).put("snapshot",state)
            save(c,r)
            state = write(c,next,state)
            check(value(c,state) == next)
            last = next
            r.put("last",last).put("snapshot",state)
            save(c,r)
            if (gradual && last != old) Thread.sleep(100)
        }
        android.util.Log.i("AerialNight", "Restored backlight to $old")
        check(prefs(c).edit().remove("restore").commit())
        failed = false
    }
    suspend fun recover(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { restore(context.applicationContext) }.onFailure { failed = true } }
    }
    suspend fun session(context: Context, minutes: Int) = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        mutex.withLock {
            var fault = false
            try {
                restore(c)
                check(mtk(c) || File("/system/framework/cn.cvte.tv.api.jar").isFile())
                var state = snapshot(c)
                val original = value(c,state)
                check(original in 5..100)
                validate(c,state,state,original)
                val record = JSONObject().put("original",original).put("last",original).put("next",original)
                    .put("mtk",mtk(c)).put("snapshot",state)
                save(c,record)
                failed = false
                android.util.Log.i("AerialNight", "Night backlight starting at $original, target 5")
                val start = SystemClock.elapsedRealtime()
                val duration = minutes * 60_000L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val target = NightBacklightPolicy.target(original,NightModeEnvelope.level(elapsed,duration,100))
                    if (target != record.getInt("last")) {
                        record.put("next",target)
                        save(c,record)
                        state = write(c,target,state)
                        check(value(c,state) == target)
                        record.put("last",target).put("snapshot",state)
                        save(c,record)
                    }
                    if (elapsed >= duration) break
                    delay(1_000)
                }
                android.util.Log.i("AerialNight", "Verified night backlight 5")
                // No periodic hardware reads or writes after reaching 5%.
                awaitCancellation()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fault = true; failed = true; android.util.Log.w("AerialNight", "Night backlight refused", e) }
            finally {
                withContext(NonCancellable) {
                    runCatching { restore(c, gradual = true) }.onFailure { failed = true }
                    if (fault) failed = true
                }
            }
        }
    }
}
