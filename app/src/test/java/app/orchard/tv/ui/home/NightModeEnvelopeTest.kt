package app.orchard.tv.ui.home

import org.junit.Assert.*
import org.junit.Test

class NightModeEnvelopeTest {
    @Test fun boundedMonotonicAndSettlesAtEverySupportedTarget() {
        for (minutes in NightModeEnvelope.minutes) for (percent in NightModeEnvelope.dimming) {
            val duration = minutes * 60_000L
            assertEquals(0f, NightModeEnvelope.level(-1, duration, percent), 0f)
            var previous = 0f
            for (step in 0..1000) {
                val current = NightModeEnvelope.level(duration * step / 1000, duration, percent)
                assertTrue(current >= previous)
                assertTrue(current <= percent / 100f)
                previous = current
            }
            assertEquals(percent / 100f, NightModeEnvelope.level(duration * 2, duration, percent), 0f)
        }
    }
    @Test fun slowSamplesCannotIntroduceVisibleOpacitySteps() {
        // Worst supported case: 90% in five minutes, at the steepest part of smoothstep.
        val duration = 300_000L
        for (time in 0L..duration step 250) {
            val delta = NightModeEnvelope.level(time + 250, duration, 90) - NightModeEnvelope.level(time, duration, 90)
            assertTrue(delta >= 0f && delta < 0.0012f)
        }
    }
}
