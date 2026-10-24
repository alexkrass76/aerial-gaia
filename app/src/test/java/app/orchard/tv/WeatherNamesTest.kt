package app.orchard.tv

import app.orchard.tv.weather.WeatherNames
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeatherNamesTest {
    @Test fun followsLanguageInsteadOfForcingChinese() {
        for (code in listOf("ru", "uk", "kk", "zh", "en")) {
            assertEquals(code, WeatherNames.language(Locale.forLanguageTag(code)))
        }
        assertEquals("en", WeatherNames.language(Locale.ROOT))
    }

    @Test fun cachedNameMustMatchExistingCoordinates() {
        val response = JSONObject("""{"results":[
            {"name":"Wrong town","latitude":20,"longitude":20},
            {"name":"Localized town","latitude":10.0001,"longitude":10}
        ]}""")
        assertEquals("Localized town", WeatherNames.nearbyName(response, 10.0, 10.0))
        assertNull(WeatherNames.nearbyName(response, 40.0, 40.0))
        assertNull(WeatherNames.nearbyName(JSONObject(), 10.0, 10.0))
    }
}
