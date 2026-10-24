package app.orchard.tv.tasks

import android.os.Looper
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeTransitionTest {
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @After fun clear() { TaskBridgeClient.onDoubleHome = null; advance(1500) }

    @Test fun waitsForHomeToResumeAndDeliversOnlyOnce() {
        var opens = 0
        TaskBridgeClient.onDoubleHome = null
        TaskBridgeClient.requestDoubleHome()
        advance(200)
        assertEquals(0, opens)
        TaskBridgeClient.onDoubleHome = { opens++ }
        advance(200)
        assertEquals(1, opens)
        TaskBridgeClient.onDoubleHome = { opens++ }
        advance(200)
        assertEquals(1, opens)
    }

    @Test fun doesNotOpenMuchLaterAfterReturningFromAnotherApp() {
        var opens = 0
        TaskBridgeClient.onDoubleHome = null
        TaskBridgeClient.requestDoubleHome()
        advance(1200)
        TaskBridgeClient.onDoubleHome = { opens++ }
        advance(200)
        assertEquals(0, opens)
    }

    @Test fun pauseBetweenReceiptAndDeliveryDoesNotLoseRequest() {
        var opens = 0
        TaskBridgeClient.onDoubleHome = { opens++ }
        TaskBridgeClient.requestDoubleHome()
        TaskBridgeClient.onDoubleHome = null
        advance(200)
        assertEquals(0, opens)
        TaskBridgeClient.onDoubleHome = { opens++ }
        advance(200)
        assertEquals(1, opens)
    }
}
