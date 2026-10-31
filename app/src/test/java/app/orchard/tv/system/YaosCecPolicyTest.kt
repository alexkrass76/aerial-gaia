package app.orchard.tv.system
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
class YaosCecPolicyTest {
    private fun state() = JSONObject("""{"cec":{"enabled":true,"auto_off":false,"auto_wake":true,"volume":true,"arc_established":false,"system_audio":false},"cec_stored":{"enabled":"1","auto_off":"0","auto_wake":"null","volume":"null"}}""")
    private fun rejects(block:()->Unit) { try {block();fail("Must refuse")} catch(e:IllegalArgumentException){} catch(e:IllegalStateException){} }
    @Test fun permitsVerifiedKeysAndMissingPersistedDefaults() {
        val s=state();for(k in YaosCecPolicy.keys.keys) YaosCecPolicy.validate(s,s,k)
        rejects { YaosCecPolicy.validate(s,s,"arc_established") }
    }
    @Test fun blocksStaleUnknownAndReceiverStates() {
        val old=state()
        rejects { YaosCecPolicy.validate(state().also { it.getJSONObject("cec").put("auto_wake",false) },old,"auto_wake") }
        rejects { YaosCecPolicy.validate(state().also { it.getJSONObject("cec_stored").put("auto_wake","1") },old,"auto_wake") }
        rejects { YaosCecPolicy.validate(state().also { it.getJSONObject("cec").remove("auto_wake") },old,"auto_wake") }
        rejects { YaosCecPolicy.validate(state().also { it.getJSONObject("cec").put("arc_established",true) },old,"enabled") }
        rejects { YaosCecPolicy.validate(state().also { it.getJSONObject("cec").remove("system_audio") },old,"enabled") }
    }
    @Test fun inconsistentOrUnknownDefaultsCannotStartNewWrite() {
        val s=state(); assertTrue(YaosCecPolicy.storedMatchesRuntime(s,"auto_wake"))
        s.getJSONObject("cec").put("auto_wake",false)
        assertFalse(YaosCecPolicy.storedMatchesRuntime(s,"auto_wake"))
        s.getJSONObject("cec_stored").put("auto_wake","0")
        assertTrue(YaosCecPolicy.storedMatchesRuntime(s,"auto_wake"))
    }
    @Test fun disabledCecAllowsMasterRestoreOnly() {
        val s=state().also { it.getJSONObject("cec").put("enabled",false).remove("arc_established");it.getJSONObject("cec_stored").put("enabled","0") }
        YaosCecPolicy.validate(s,s,"enabled")
        rejects { YaosCecPolicy.validate(s,s,"auto_wake") }
    }
}
