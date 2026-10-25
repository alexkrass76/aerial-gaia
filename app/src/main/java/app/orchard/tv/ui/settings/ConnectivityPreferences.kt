package app.orchard.tv.ui.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.orchard.tv.system.GaiaBluetooth
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.tasks.TaskBridgeClient
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** User-triggered snapshots, no background polling, Wi-Fi credentials or firmware activities. */
@Composable
internal fun ConnectivityPreferences(bluetooth: Boolean, vm: HomeViewModel, revision: Int) {
    val context = LocalContext.current
    var snapshot by remember(bluetooth) { mutableStateOf<JSONObject?>(null) }
    var failed by remember(bluetooth) { mutableStateOf(false) }
    var busy by remember(bluetooth) { mutableStateOf(false) }
    var refresh by remember(bluetooth) { mutableIntStateOf(0) }
    var selected by remember(bluetooth) { mutableStateOf<Pair<Int, String>?>(null) }
    var newNetwork by remember(bluetooth) { mutableStateOf<Triple<String, String, Int>?>(null) }
    var requested by remember(bluetooth) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bluetooth, revision, refresh) {
        val result = withContext(Dispatchers.IO) { runCatching { if (bluetooth) GaiaBluetooth.snapshot(context) else JSONObject(TaskBridgeClient.request(10) ?: error("Unavailable")) } }
        snapshot = result.getOrNull()
        failed = result.isFailure
    }
    fun action(code: Int, id: Int? = null) {
        if (busy) return
        vm.whenUnlocked {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    check(!AerialSettings.locked)
                    TaskBridgeClient.request(code, id)
                } }
                failed = result.isFailure
                requested = code == 12 && result.isSuccess
                selected = null
                if (result.isSuccess) { delay(3000); refresh++ }
                busy = false
            }
        }
    }
    newNetwork?.let { (ssid, security, savedId) ->
        WifiJoinForm(ssid, security, savedId, vm, onBack = { newNetwork = null }, onAccepted = {
            newNetwork = null; requested = true; refresh++
        })
        return
    }
    if (failed) BasicText(stringResource(R.string.gs_connections_failed), style = Type.Caption)
    if (busy) BasicText(stringResource(R.string.tasks_loading), style = Type.Caption)
    SettingRow(Glyph.Gear, stringResource(R.string.gs_refresh)) { if (!busy) { requested = false; refresh++ } }
    if (bluetooth) {
        snapshot?.let { BasicText(stringResource(if (it.getBoolean("enabled")) R.string.perm_on else R.string.perm_off), style = Type.Body) }
        BasicText(stringResource(R.string.gs_bonded_devices), style = Type.Headline)
        val devices = snapshot?.optJSONArray("devices")
        if (snapshot != null && (devices == null || devices.length() == 0)) BasicText(stringResource(R.string.gs_none), style = Type.Body)
        for (i in 0 until (devices?.length() ?: 0)) {
            val device = devices!!.getJSONObject(i)
            BasicText(device.optString("name").ifBlank { device.optString("address") }, style = Type.Body)
        }
        BluetoothPairing(vm) { refresh++ }
    } else {
        SettingRow(Glyph.Wifi, stringResource(R.string.gs_scan_wifi)) { action(11) }
        if (requested) BasicText(stringResource(R.string.gs_connection_requested), style = Type.Caption)
        selected?.let { (id, name) ->
            BasicText(stringResource(R.string.gs_connect_confirm, name), style = Type.Body)
            SettingRow(Glyph.Wifi, stringResource(R.string.gs_connect)) { action(12, id) }
            val profiles = snapshot?.optJSONArray("saved")
            val profile = (0 until (profiles?.length() ?: 0)).map { profiles!!.getJSONObject(it) }.firstOrNull { it.getInt("id") == id }
            if (profile?.optString("security") == "wpa2") SettingRow(Glyph.Gear, stringResource(R.string.gs_change_wifi_password)) {
                vm.whenUnlocked { newNetwork = Triple(name, "wpa2", id) }
            }
            SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back)) { selected = null }
        }
        BasicText(stringResource(R.string.gs_saved_networks), style = Type.Headline)
        val saved = snapshot?.optJSONArray("saved")
        if (snapshot != null && (saved == null || saved.length() == 0)) BasicText(stringResource(R.string.gs_none), style = Type.Body)
        for (i in 0 until (saved?.length() ?: 0)) {
            val row = saved!!.getJSONObject(i)
            val current = row.getInt("id") == snapshot?.optInt("current", -1)
            SettingRow(Glyph.Wifi, row.getString("ssid"), detail = stringResource(if (current) R.string.gs_selected else R.string.gs_connect)) {
                if (!busy) selected = row.getInt("id") to row.getString("ssid")
            }
        }
        BasicText(stringResource(R.string.gs_nearby_networks), style = Type.Headline)
        BasicText(stringResource(R.string.gs_wifi_supported), style = Type.Caption)
        val nearby = snapshot?.optJSONArray("nearby")
        for (i in 0 until (nearby?.length() ?: 0)) {
            val row = nearby!!.getJSONObject(i)
            val ssid = row.getString("ssid")
            val security = row.optString("security", "unsupported")
            val alreadySaved = (0 until (saved?.length() ?: 0)).any { saved!!.getJSONObject(it).optString("ssid") == ssid }
            SettingRow(Glyph.Wifi, ssid, detail = if (security == "unsupported") stringResource(R.string.gs_wifi_unsupported)
                else if (alreadySaved) stringResource(R.string.gs_saved_networks) else "${row.getInt("level")} dBm · ${if (security == "wpa2") "WPA2" else stringResource(R.string.gs_wifi_open)}") {
                if (!busy && !alreadySaved && security in listOf("open", "wpa2")) vm.whenUnlocked { newNetwork = Triple(ssid, security, -1) }
            }
        }
    }
}
