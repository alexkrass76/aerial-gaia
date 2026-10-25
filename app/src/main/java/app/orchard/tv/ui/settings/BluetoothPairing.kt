package app.orchard.tv.ui.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.system.GaiaBluetoothDiscovery
import app.orchard.tv.ui.theme.*

@Composable
internal fun BluetoothPairing(vm: HomeViewModel, onChanged: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val callback by rememberUpdatedState(onChanged)
    val discovery = remember { GaiaBluetoothDiscovery(context) { revision++; callback() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(discovery, owner) {
        discovery.register()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) discovery.stopScan()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); discovery.close() }
    }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Reads revision so receiver callbacks update the observed device list.
    val devices = remember(revision) { discovery.devices }
    SettingRow(Glyph.Bluetooth, stringResource(if (discovery.scanning) R.string.gs_stop_search else R.string.gs_find_devices)) {
        vm.whenUnlocked {
            if (discovery.scanning) discovery.stopScan() else discovery.startScan()
        }
    }
    BasicText(stringResource(R.string.gs_pairing_hint), style = Type.Caption)
    if (failed || discovery.failed) BasicText(stringResource(R.string.gs_connections_failed), style = Type.Caption)
    if (discovery.pairing != null) BasicText(stringResource(R.string.gs_pairing_wait), style = Type.Body)
    selected?.let { (address, name) ->
        BasicText("$name · $address", style = Type.Body)
        SettingRow(Glyph.Bluetooth, stringResource(R.string.gs_pair)) {
            vm.whenUnlocked {
                failed = runCatching { discovery.pair(address) }.isFailure
                selected = null
            }
        }
        SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back)) { selected = null }
    }
    devices.forEach { device ->
        val label = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address
        SettingRow(Glyph.Bluetooth, label, detail = device.address) {
            if (discovery.pairing == null) selected = device.address to label
        }
    }
}
