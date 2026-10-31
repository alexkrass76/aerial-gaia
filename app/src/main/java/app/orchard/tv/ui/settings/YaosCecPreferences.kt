package app.orchard.tv.ui.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.system.YaosCecControl
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun YaosCecPreferences(revision: Int, vm: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var writeFailed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf(YaosCecControl.hasUndo(context)) }
    var edit by remember { mutableStateOf<Pair<String,Boolean>?>(null) }
    val labels = listOf("enabled" to R.string.gs_cec_title,"auto_off" to R.string.gs_cec_device_off,
        "auto_wake" to R.string.gs_cec_wakeup,"volume" to R.string.gs_cec_volume,
        "system_audio" to R.string.gs_cec_system_audio,"arc_established" to R.string.gs_cec_arc_established)
    LaunchedEffect(revision,refresh) {
        busy=true
        try { data=withContext(Dispatchers.IO) { YaosCecControl.snapshot(context) }; failed=false }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { data=null;failed=true }
        finally { busy=false }
    }
    fun apply(undoChange: Boolean) {
        if(busy) return
        val expected=data ?: return
        val selection=edit
        vm.whenUnlocked {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if(undoChange) YaosCecControl.undo(context)
                        else YaosCecControl.change(context,requireNotNull(selection).first,selection.second,expected)
                    }
                    writeFailed=false
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { writeFailed=true }
                finally { undo=YaosCecControl.hasUndo(context);edit=null;busy=false;refresh++ }
            }
        }
    }
    if(undo) SettingRow(Glyph.ChevronLeft,stringResource(R.string.gs_cec_undo)) { apply(true) }
    if(failed || writeFailed) BasicText(stringResource(R.string.gs_cec_write_failed),style=Type.Caption)
    edit?.let { (key,target) ->
        androidx.activity.compose.BackHandler { if(!busy) edit=null }
        val label=labels.first { it.first==key }.second
        BasicText(stringResource(R.string.gs_av_confirm,stringResource(label)+": "+stringResource(if(target) R.string.st_on else R.string.st_off)),style=Type.Body)
        BasicText(stringResource(R.string.gs_cec_write_hint),style=Type.Caption)
        SettingRow(Glyph.Check,stringResource(R.string.gs_av_apply)) { apply(false) }
        SettingRow(Glyph.ChevronLeft,stringResource(R.string.gs_back)) { if(!busy) edit=null }
        if(busy) BasicText(stringResource(R.string.tasks_loading),style=Type.Caption)
        return
    }
    SettingRow(Glyph.Gear,stringResource(R.string.gs_refresh)) { if(!busy) refresh++ }
    if(busy) BasicText(stringResource(R.string.tasks_loading),style=Type.Caption)
    BasicText(stringResource(R.string.gs_cec_write_hint),style=Type.Caption)
    val state=data?.optJSONObject("cec")
    for((key,label) in labels) {
        val value=state?.opt(key) as? Boolean
        val detail=stringResource(if(value==null) R.string.gs_unknown else if(value) R.string.st_on else R.string.st_off)
        val safe=state?.opt("arc_established")==false && state.opt("system_audio")==false
        val disabled=state?.opt("enabled")==false
        if(value!=null && key in listOf("enabled","auto_off","auto_wake","volume") &&
            ((key=="enabled" && disabled) || (safe && state?.opt("enabled")==true))) {
            SettingRow(Glyph.Gear,stringResource(label),detail=detail) { if(!busy) edit=key to !value }
        } else SettingsValueRow(Glyph.Info,stringResource(label),detail)
    }
    val ports=state?.optJSONArray("arc_ports")
    SettingsValueRow(Glyph.Info,stringResource(R.string.gs_cec_arc_ports),
        if(ports==null) stringResource(R.string.gs_unknown) else if(ports.length()==0) "—"
        else (0 until ports.length()).joinToString(", ") { "HDMI "+ports.getInt(it) })
}
