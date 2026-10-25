package app.orchard.tv.update

import android.content.Context
import app.orchard.tv.BuildConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GaiaUpdatesTest {
    @Test fun cachedUpstreamReleaseIsDiscardedAndForcedChecksStayDisabled() {
        assumeTrue(Updates.isGaia)
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        prefs.edit().putString("latest", "999.0").putLong("checked_at", 123L).commit()
        Updates.init(context)
        assertNull(Updates.available)
        assertFalse(prefs.contains("latest"))
        assertFalse(prefs.contains("checked_at"))
        Updates.checkIfStale(force = true)
        assertNull(Updates.available)
        assertFalse(prefs.contains("checked_at"))
    }
    @Test fun gaiaCannotRunUpstreamInstaller() {
        assumeTrue(Updates.isGaia)
        val context = RuntimeEnvironment.getApplication()
        assertFalse(Updates.canInstall(context))
        Updates.install(context)
        assertEquals(Updates.Install.Failed(Updates.Failure.NotOfficial), Updates.install)
    }
    @Test fun manualLinksOnlyUseForkAndDoNotIncludeDeviceAddress() {
        assumeTrue(Updates.isGaia)
        assertEquals(Updates.GAIA_RELEASES, Updates.helpUrl(false, "192.0.2.1"))
        assertEquals(Updates.GAIA_GUIDE, Updates.helpUrl(true, "192.0.2.1"))
        assertEquals(BuildConfig.VERSION_NAME, Updates.current)
    }
}
