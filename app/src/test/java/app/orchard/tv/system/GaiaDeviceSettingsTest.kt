package app.orchard.tv.system

import android.provider.Settings
import app.orchard.tv.settings.AerialSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GaiaDeviceSettingsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val key = GaiaDeviceSettings.Key.WINDOW_ANIMATION
    @Before fun prepare() { AerialSettings.init(context) }
    @Test fun writesAndReadsBack() {
        Settings.Global.putString(context.contentResolver, key.setting, "1.0")
        GaiaDeviceSettings.set(context, key, "1.0", "0.5")
        assertEquals("0.5", GaiaDeviceSettings.read(context, key))
    }
    @Test fun rejectsStaleUserInterfaceWithoutOverwriting() {
        Settings.Global.putString(context.contentResolver, key.setting, "0")
        assertThrows(IllegalStateException::class.java) { GaiaDeviceSettings.set(context, key, "1", "0.5") }
        assertEquals("0", GaiaDeviceSettings.read(context, key))
    }
    @Test fun rejectsOutOfRangeValue() {
        assertThrows(IllegalArgumentException::class.java) { GaiaDeviceSettings.set(context, key, null, "100") }
    }
    @Test fun acceptsProviderNumericNormalization() {
        assertTrue(GaiaDeviceSettings.equivalent("1.0", "1"))
        assertFalse(GaiaDeviceSettings.equivalent(null, "0"))
        assertFalse(GaiaDeviceSettings.equivalent("0.5", "1"))
    }
    @Test fun cannotSelectUnknownKeyboard() {
        assertThrows(IllegalArgumentException::class.java) {
            GaiaDeviceSettings.selectKeyboard(context, "untrusted/.Keyboard", null)
        }
    }
}
