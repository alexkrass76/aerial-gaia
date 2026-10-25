package app.orchard.tv.system

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import app.orchard.tv.settings.AerialSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GaiaBluetoothDiscoveryTest {
    private val context = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        AerialSettings.init(context)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
    }
    @Test fun refusesPairingAnUndiscoveredAddress() {
        val discovery = GaiaBluetoothDiscovery(context) {}
        discovery.register()
        try {
            assertThrows(IllegalStateException::class.java) { discovery.pair("00:11:22:33:44:55") }
        } finally { discovery.close() }
    }
    @Test fun scanStopsAtDeadline() {
        val discovery = GaiaBluetoothDiscovery(context) {}
        discovery.register()
        try {
            discovery.startScan()
            assertTrue(discovery.scanning)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
            assertFalse(discovery.scanning)
            assertFalse(BluetoothAdapter.getDefaultAdapter().isDiscovering)
        } finally { discovery.close() }
    }
    @Test fun closingPageStopsItsOwnScan() {
        val discovery = GaiaBluetoothDiscovery(context) {}
        discovery.register()
        discovery.startScan()
        discovery.close()
        assertFalse(BluetoothAdapter.getDefaultAdapter().isDiscovering)
    }
    @Test fun doesNotCancelAnotherOwnersScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        adapter.startDiscovery()
        val discovery = GaiaBluetoothDiscovery(context) {}
        discovery.register()
        discovery.startScan()
        assertTrue(discovery.failed)
        discovery.close()
        assertTrue(adapter.isDiscovering)
    }
}
