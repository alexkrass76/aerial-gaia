package app.orchard.tv.system

import aerial.probe.MediaTekWritePolicy as Policy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MediaTekWritePolicyTest {
    private fun state() = JSONObject("""{"source":"HDMI 2","items":[
        {"key":"g_video__picture_mode","raw":9},
        {"key":"g_audio__aud_out_port","raw":1},
        {"key":"g_audio__spdif","raw":2},
        {"key":"g_video__brightness","raw":50,"rangeRaw":6553600},
        {"key":"g_audio__aud_balance","raw":0,"rangeRaw":3342286},
        {"key":"g_disp__disp_back_light","raw":100,"rangeRaw":6553600,"visible":0,"enabled":0}]}""")
    private fun rejects(block: () -> Unit) { try { block(); fail("Must reject") } catch (e: IllegalArgumentException) {} catch (e: IllegalStateException) {} }
    @Test fun acceptsOnlyBoundedScalarCas() {
        val s=state();Policy.validate(s,s,"g_video__brightness",51);Policy.validate(s,s,"g_audio__aud_balance",-5)
        rejects { Policy.validate(s,s,"g_cec__cec_func",1) }
        Policy.validate(s,s,"g_disp__disp_back_light",95)
        rejects { Policy.validate(s,s,"g_disp__disp_back_light",0) }
        rejects { Policy.validate(s,s,"g_disp__disp_back_light",101) }
        rejects { Policy.validate(s,s,"g_video__brightness",101) }
        rejects { Policy.validate(s,s,"g_audio__aud_balance",-51) }
    }
    @Test fun rejectsStaleValueContextAndRange() {
        val before=state()
        rejects { Policy.validate(state().also { Policy.item(it,"g_disp__disp_back_light").put("enabled",1) },before,"g_disp__disp_back_light",95) }
        rejects { Policy.validate(state().put("source","HDMI 1"),before,"g_video__brightness",51) }
        rejects { Policy.validate(state().also { Policy.item(it,"g_video__picture_mode").put("raw",0) },before,"g_video__brightness",51) }
        rejects { Policy.validate(state().also { Policy.item(it,"g_audio__aud_out_port").put("raw",2) },before,"g_audio__aud_balance",1) }
        rejects { Policy.validate(state().also { Policy.item(it,"g_video__brightness").put("raw",55) },before,"g_video__brightness",51) }
        rejects { Policy.validate(state().also { Policy.item(it,"g_video__brightness").put("rangeRaw",0) },before,"g_video__brightness",51) }
    }
}
