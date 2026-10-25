package app.orchard.tv.system

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GaiaAvPolicyTest {
    private fun state() = JSONObject("""{
        "source":"0:INPUTSOURCE_STORAGE",
        "backlight":{"value":100,"status":"ITEM_STATUS_ENABLE"},
        "picture":{"mode":"OPTION_PICTURE_MODE_USER","modes":["OPTION_PICTURE_MODE_USER","OPTION_PICTURE_MODE_SOFT"],
            "items":[{"key":"PICTURE_BRIGHTNESS","value":50,"status":"ITEM_STATUS_ENABLE"}]},
        "digital":{"mode":"SPDIF_OUT_PCM","modes":["SPDIF_OUT_PCM","SPDIF_OUT_RAW"]}
    }""")
    @Test fun permitsBoundedBacklightAndUserPicture() {
        val s = state()
        GaiaAvPolicy.validate(s, s, "backlight", "95")
        GaiaAvPolicy.validate(s, s, "PICTURE_BRIGHTNESS", "55")
        GaiaAvPolicy.validate(s, s, "digital", "SPDIF_OUT_RAW")
        GaiaAvPolicy.validate(s, s, "picture_mode", "OPTION_PICTURE_MODE_SOFT")
    }
    @Test fun rejectsStaleInputOrValue() {
        val expected = state()
        val moved = state().put("source", "5:INPUTSOURCE_HDMI")
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(moved, expected, "backlight", "95") }
        val changed = state().apply { getJSONObject("backlight").put("value", 90) }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(changed, expected, "backlight", "95") }
    }
    @Test fun cannotAdjustPictureInPresetOrWhenFirmwareDisablesIt() {
        val preset = state().apply { getJSONObject("picture").put("mode", "OPTION_PICTURE_MODE_SOFT") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(preset, preset, "PICTURE_BRIGHTNESS", "55") }
        val disabled = state().apply { getJSONObject("picture").getJSONArray("items").getJSONObject(0).put("status", "ITEM_STATUS_DISABLE") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(disabled, disabled, "PICTURE_BRIGHTNESS", "55") }
    }
    @Test fun backlightAlsoChecksPictureMode() {
        val current = state().apply { getJSONObject("picture").put("mode", "OPTION_PICTURE_MODE_SOFT") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(current, state(), "backlight", "95") }
    }
    @Test fun rejectsUnsupportedModesAndRouting() {
        val s = state()
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "digital", "SPDIF_OUT_AUTO") }
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "picture_mode", "OPTION_PICTURE_MODE_DYNAMIC") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(s, s, "sound", "OPTION_SOUND_OUT_ARC") }
    }
    @Test fun backlightNeverWritesBlackAndUndoCanRestoreDimOriginal() {
        val s = state()
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "backlight", "0", restoring = true) }
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "backlight", "1") }
        GaiaAvPolicy.validate(s, s, "backlight", "1", restoring = true)
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "backlight", "101") }
    }
    @Test fun rejectsUnavailableSourceAndOutOfRangePicture() {
        val s = state()
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "PICTURE_BRIGHTNESS", "-1") }
        val unknown = state().put("source", "0:null")
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(unknown, unknown, "backlight", "95") }
    }
    private fun cecState() = state().put("cec", JSONObject().put("enabled", true).put("cec_device_off", false).put("cec_wakeup", false))
        .put("arc", false).put("sound", JSONObject().put("mode", "OPTION_SOUND_OUT_SPEAKER"))
    @Test fun permitsCecPermissionsWithoutRoutingOrMasterSwitch() {
        val s = cecState()
        GaiaAvPolicy.validate(s, s, "cec_device_off", "true")
        GaiaAvPolicy.validate(s, s, "cec_wakeup", "true")
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(s, s, "cec_enabled", "false") }
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "cec_wakeup", "1") }
    }
    @Test fun rejectsMaskedCecFlagsAndChangedAudioState() {
        val disabled = cecState().apply { getJSONObject("cec").put("enabled", false) }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(disabled, disabled, "cec_device_off", "true") }
        val changed = cecState().put("arc", true)
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(changed, cecState(), "cec_wakeup", "true") }
        val routed = cecState().apply { getJSONObject("sound").put("mode", "OPTION_SOUND_OUT_ARC") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(routed, cecState(), "cec_wakeup", "true") }
    }
    @Test fun rejectsStaleCecPermissionAndMissingRead() {
        val changed = cecState().apply { getJSONObject("cec").put("cec_wakeup", true) }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(changed, cecState(), "cec_wakeup", "false") }
        val missing = cecState().apply { remove("arc") }
        assertThrows(org.json.JSONException::class.java) { GaiaAvPolicy.validate(missing, missing, "cec_wakeup", "true") }
    }

    @Test fun speakerBalanceIsBoundedAndNeedsLocalSpeakers() {
        val s = cecState().put("speaker_balance", 0)
        GaiaAvPolicy.validate(s, s, "speaker_balance", "-50")
        GaiaAvPolicy.validate(s, s, "speaker_balance", "50")
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "speaker_balance", "51") }
        assertThrows(IllegalArgumentException::class.java) { GaiaAvPolicy.validate(s, s, "speaker_balance", "-51") }
        val arc = JSONObject(s.toString()).put("arc", true)
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(arc, s, "speaker_balance", "5") }
        val spdif = JSONObject(s.toString()).apply { getJSONObject("sound").put("mode", "OPTION_SOUND_OUT_SPDIF") }
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(spdif, s, "speaker_balance", "5") }
    }
    @Test fun speakerBalanceRefusesStaleValuesAndMissingArcState() {
        val s = cecState().put("speaker_balance", 0)
        val changed = JSONObject(s.toString()).put("speaker_balance", 5)
        assertThrows(IllegalStateException::class.java) { GaiaAvPolicy.validate(changed, s, "speaker_balance", "10") }
        val unavailable = JSONObject(s.toString()).apply { remove("arc") }
        assertThrows(org.json.JSONException::class.java) { GaiaAvPolicy.validate(unavailable, unavailable, "speaker_balance", "5") }
    }

}
