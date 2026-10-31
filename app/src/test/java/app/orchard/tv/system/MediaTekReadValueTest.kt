package app.orchard.tv.system

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MediaTekReadValueTest {
    private fun row(value: Int, low: Int, high: Int) = JSONObject()
        .put("raw", value).put("rangeRaw", (high shl 16) or (low and 65535))
    @Test fun acceptsSignedBalanceAndPictureRange() {
        assertEquals(-20, MediaTekReadValue.scalar(row(-20, -50, 50)))
        assertEquals(50, MediaTekReadValue.scalar(row(50, 0, 100)))
    }
    @Test fun refusesMissingUnsupportedAndOutOfRangeResults() {
        assertNull(MediaTekReadValue.scalar(JSONObject()))
        assertNull(MediaTekReadValue.scalar(row(0, 0, 0)))
        assertNull(MediaTekReadValue.scalar(row(-4, 0, 100)))
        assertNull(MediaTekReadValue.scalar(row(101, 0, 100)))
        assertNull(MediaTekReadValue.scalar(row(50, 100, 0)))
        assertNull(MediaTekReadValue.scalar(row(50, 0, 100).put("error", "JNI unavailable")))
    }
}
