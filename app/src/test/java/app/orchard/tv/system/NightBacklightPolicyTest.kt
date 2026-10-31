package app.orchard.tv.system
import org.junit.Assert.*
import org.junit.Test
class NightBacklightPolicyTest {
    @Test fun dimsMonotonicallyToFiveWithoutBlackout() {
        for (original in 5..100) {
            var previous = original
            for (step in 0..1000) {
                val value = NightBacklightPolicy.target(original,step/1000f)
                assertTrue(value in 5..original)
                assertTrue(value <= previous)
                previous = value
            }
            assertEquals(5,previous)
            assertEquals(original,NightBacklightPolicy.target(original,-1f))
            assertEquals(5,NightBacklightPolicy.target(original,2f))
        }
    }
}
