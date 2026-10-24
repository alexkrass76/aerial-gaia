package app.orchard.tv

import android.view.KeyEvent
import app.orchard.tv.ui.home.ScreensaverSkips
import app.orchard.tv.wallpaper.Aerials
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreensaverInfoTest {
    @Test fun everyDirectionRevealsInformationForTenSeconds() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val state = ScreensaverSkips()
            assertFalse(state.infoVisible(100))
            assertTrue(state.onKey(KeyEvent(KeyEvent.ACTION_DOWN, key), 100))
            assertTrue(state.infoVisible(10_099))
            assertFalse(state.infoVisible(10_100))
            assertTrue(state.onKey(KeyEvent(KeyEvent.ACTION_UP, key), 500))
            assertEquals(10_100L, state.infoUntil)
        }
    }

    @Test fun repeatedDirectionExtendsAndNewSessionStartsHidden() {
        val state = ScreensaverSkips()
        state.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP), 100)
        state.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT), 9_000)
        assertTrue(state.infoVisible(18_999))
        assertFalse(state.infoVisible(19_000))
        state.reset()
        assertFalse(state.infoVisible(9_001))
    }

    @Test fun onlyBackIsReturnedToHostForExit() {
        val state = ScreensaverSkips()
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertTrue(state.onKey(KeyEvent(KeyEvent.ACTION_DOWN, key), 100))
            assertTrue(state.onKey(KeyEvent(KeyEvent.ACTION_UP, key), 101))
        }
        assertEquals(0L, state.infoUntil)
        assertFalse(state.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), 100))
    }

    @Test fun allFilmsHaveRussianAndUkrainianTimedCaptions() {
        val context = RuntimeEnvironment.getApplication()
        val english = Aerials.load(context, "en")
        for (language in listOf("ru", "uk")) {
            val translated = Aerials.load(context, language)
            assertEquals(114, translated.size)
            assertEquals(english.map { it.id }, translated.map { it.id })
            for (film in translated) {
                assertTrue(film.captions.isNotEmpty())
                assertTrue(film.captions.all { it.second.isNotBlank() })
                assertEquals(film.captions.first().second, film.captionAt(-1))
            }
            assertNotEquals(english.first().captionAt(0), translated.first().captionAt(0))
        }
    }
}
