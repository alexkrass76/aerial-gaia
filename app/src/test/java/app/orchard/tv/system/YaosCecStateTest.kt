package app.orchard.tv.system

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class YaosCecStateTest {
    @Test fun parsesRuntimeBooleansAndArcCapabilitySeparately() {
        val s = YaosCecState.parse("""
            mHdmiControlEnabled: true
            mHdmiCecVolumeControlEnabled true
            mAutoDeviceOff: false
            mAutoWakeup: true
            mArcEstablished: false
            mSystemAudioActivated: false
            port_id: 2, type: HDMI_IN, address: 0x2000, cec: true, arc: true, mhl: false
        """.trimIndent())
        assertTrue(s.getBoolean("enabled")); assertTrue(s.getBoolean("auto_wake"))
        assertTrue(s.getBoolean("volume")); assertFalse(s.getBoolean("auto_off"))
        assertFalse(s.getBoolean("arc_established")); assertEquals(2,s.getJSONArray("arc_ports").getInt(0))
    }
    @Test fun absentMalformedConflictingAndHistoryRemainUnknown() {
        assertFalse(YaosCecState.parse("Permission denied").has("enabled"))
        assertFalse(YaosCecState.parse("mAutoWakeup: null").has("auto_wake"))
        assertFalse(YaosCecState.parse("mAutoWakeup: true\nmAutoWakeup: false").has("auto_wake"))
        assertFalse(YaosCecState.parse("CEC message history:\n mAutoWakeup: true").has("auto_wake"))
        assertFalse(YaosCecState.parse("").has("arc_ports"))
    }
}
