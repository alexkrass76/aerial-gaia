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
    @Test fun captionsUseSecureTableAndAreVisibleToAndroid() {
        val enabled = GaiaDeviceSettings.Key.CAPTIONS_ENABLED
        GaiaDeviceSettings.set(context, enabled, GaiaDeviceSettings.read(context, enabled), "1")
        val manager = context.getSystemService(android.content.Context.CAPTIONING_SERVICE) as android.view.accessibility.CaptioningManager
        assertTrue(manager.isEnabled)
        val scale = GaiaDeviceSettings.Key.CAPTIONS_SCALE
        GaiaDeviceSettings.set(context, scale, GaiaDeviceSettings.read(context, scale), "1.5")
        assertEquals(1.5f, manager.fontScale, 0.001f)
    }
    @Test fun changingCaptionPresetPreservesCustomColorsAndCanReturnToCustom() {
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, "accessibility_captioning_foreground_color", -65536)
        val style = GaiaDeviceSettings.Key.CAPTIONS_STYLE
        Settings.Secure.putString(resolver, style.setting, "-1")
        GaiaDeviceSettings.set(context, style, "-1", "2")
        assertEquals(-65536, Settings.Secure.getInt(resolver, "accessibility_captioning_foreground_color"))
        GaiaDeviceSettings.set(context, style, "2", "-1")
        assertEquals("-1", GaiaDeviceSettings.read(context, style))
    }
    @Test fun staleCaptionSettingIsNotOverwritten() {
        val enabled = GaiaDeviceSettings.Key.CAPTIONS_ENABLED
        Settings.Secure.putString(context.contentResolver, enabled.setting, "1")
        assertThrows(IllegalStateException::class.java) {
            GaiaDeviceSettings.set(context, enabled, "0", "0")
        }
        assertEquals("1", GaiaDeviceSettings.read(context, enabled))
    }
    @Test fun rejectsInvalidCaptionValues() {
        for ((key, value) in listOf(GaiaDeviceSettings.Key.CAPTIONS_ENABLED to "2",
            GaiaDeviceSettings.Key.CAPTIONS_STYLE to "5", GaiaDeviceSettings.Key.CAPTIONS_SCALE to "NaN")) {
            assertThrows(IllegalArgumentException::class.java) {
                GaiaDeviceSettings.set(context, key, GaiaDeviceSettings.read(context, key), value)
            }
        }
    }
}
