package app.orchard.tv.tasks

import android.os.Binder
import android.os.Parcel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskBridgeDeviceProtocolTest {
    @After fun clear() { TaskBridgeClient.binder = null }
    @Test fun wifiSnapshotReadsStructuredReply() {
        TaskBridgeClient.binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("app.orchard.tv.gaia.tasks.v1")
                assertEquals(10, code)
                reply!!.writeNoException()
                reply.writeString("{\"saved\":[]}")
                return true
            }
        }
        assertEquals("{\"saved\":[]}", TaskBridgeClient.request(10))
    }
    @Test fun connectionPassesOnlyNumericSavedIdAndPropagatesRejection() {
        TaskBridgeClient.binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("app.orchard.tv.gaia.tasks.v1")
                assertEquals(12, code)
                assertEquals(7, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeException(IllegalArgumentException("Network no longer saved"))
                return true
            }
        }
        assertThrows(IllegalArgumentException::class.java) { TaskBridgeClient.request(12, 7) }
    }
    @Test fun credentialsUsePrivateParcelWithExplicitProfileId() {
        app.orchard.tv.settings.AerialSettings.init(org.robolectric.RuntimeEnvironment.getApplication())
        TaskBridgeClient.binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("app.orchard.tv.gaia.tasks.v1")
                assertEquals(13, code)
                assertEquals("Test", data.readString())
                assertEquals("wpa2", data.readString())
                assertEquals("example-password", data.readString())
                assertEquals(-1, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeNoException()
                reply.writeString("{\"id\":7}")
                return true
            }
        }
        assertEquals("{\"id\":7}", TaskBridgeClient.addWifi("Test", "wpa2", "example-password"))
    }

}
