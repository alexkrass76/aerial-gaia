package app.orchard.tv.system

import gaia.bridge.WifiInputPolicy
import org.junit.Assert.*
import org.junit.Test

class WifiInputPolicyTest {
    @Test fun distinguishesSupportedSecurityWithoutDowngrading() {
        assertEquals("open", WifiInputPolicy.security("[ESS]"))
        assertEquals("wpa2", WifiInputPolicy.security("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("wpa2", WifiInputPolicy.security("[WPA2-PSK+SAE-CCMP][ESS]"))
        for (s in listOf("[WPA3-SAE][ESS]", "[OWE][ESS]", "[WEP][ESS]", "[WPA2-EAP][ESS]", "[WPA-PSK][ESS]", ""))
            assertEquals(s, "unsupported", WifiInputPolicy.security(s))
    }
    @Test fun validatesSsidInBytes() {
        WifiInputPolicy.validate("я".repeat(16), "open", "")
        for (ssid in listOf("я".repeat(17), "", "name\n", "x\u0000"))
            assertThrows(IllegalArgumentException::class.java) { WifiInputPolicy.validate(ssid, "open", "") }
    }
    @Test fun validatesWpa2PasswordBoundaries() {
        for (secret in listOf("a".repeat(8), "a".repeat(63), "a1".repeat(32))) WifiInputPolicy.validate("Test", "wpa2", secret)
        for (secret in listOf("short", "g".repeat(64), "a".repeat(65), "пароль123", "password\n"))
            assertThrows(IllegalArgumentException::class.java) { WifiInputPolicy.validate("Test", "wpa2", secret) }
    }
    @Test fun rejectsCredentialsForOpenNetwork() {
        assertThrows(IllegalArgumentException::class.java) { WifiInputPolicy.validate("Test", "open", "not-empty") }
    }
    @Test fun rejectsUnknownSecurity() {
        assertThrows(IllegalArgumentException::class.java) { WifiInputPolicy.validate("Test", "wep", "password") }
    }
    @Test fun quotesAndroidValuesRatherThanBuildingShellCommands() {
        assertEquals("\"a\\\"b\\\\c\"", WifiInputPolicy.quote("a\"b\\c"))
        assertEquals("\"\$(id)\"", WifiInputPolicy.quote("\$(id)"))
    }
}
