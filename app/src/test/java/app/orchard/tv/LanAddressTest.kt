package app.orchard.tv

import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import app.orchard.tv.system.LanAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LanAddressTest {
    private fun candidate(ip: String, vararg transports: Int, active: Boolean = false): LanAddress.Candidate {
        val caps = NetworkCapabilities().apply {
            transports.forEach { NetworkCapabilities::class.java.getMethod("addTransportType", Int::class.javaPrimitiveType).invoke(this, it) }
        }
        val parts = ip.split('/')
        val address = LinkAddress::class.java.getConstructor(java.net.InetAddress::class.java, Int::class.javaPrimitiveType)
            .newInstance(java.net.InetAddress.getByName(parts[0]), parts[1].toInt())
        val links = LinkProperties().apply {
            LinkProperties::class.java.getMethod("addLinkAddress", LinkAddress::class.java).invoke(this, address)
        }
        return LanAddress.Candidate(caps, links, active)
    }

    @Test fun excludesVpnEvenWhenItAdvertisesWifi() {
        val vpn = candidate("10.0.0.2/24", NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI, active = true)
        val wifi = candidate("192.168.10.2/24", NetworkCapabilities.TRANSPORT_WIFI)
        assertEquals("192.168.10.2", LanAddress.select(listOf(vpn, wifi)))
        assertNull(LanAddress.select(listOf(vpn)))
    }

    @Test fun localNetworkDoesNotRequireInternetValidation() {
        assertEquals("192.168.10.2", LanAddress.select(listOf(candidate("192.168.10.2/24", NetworkCapabilities.TRANSPORT_WIFI))))
    }

    @Test fun prefersDefaultLanOtherwiseEthernet() {
        val ethernet = candidate("192.168.20.2/24", NetworkCapabilities.TRANSPORT_ETHERNET)
        val wifi = candidate("192.168.10.2/24", NetworkCapabilities.TRANSPORT_WIFI)
        assertEquals("192.168.20.2", LanAddress.select(listOf(wifi, ethernet)))
        assertEquals("192.168.10.2", LanAddress.select(listOf(ethernet, wifi.copy(active = true))))
    }

    @Test fun skipsUnusableAddressesAndCellular() {
        val wifi = candidate("169.254.1.2/16", NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = candidate("10.0.0.2/24", NetworkCapabilities.TRANSPORT_CELLULAR)
        assertNull(LanAddress.select(listOf(wifi, cellular)))
        val ethernet = candidate("192.168.20.2/24", NetworkCapabilities.TRANSPORT_ETHERNET)
        assertEquals("192.168.20.2", LanAddress.select(listOf(wifi.copy(active = true), ethernet)))
    }
}
