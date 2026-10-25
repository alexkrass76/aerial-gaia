package app.orchard.tv.ui.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.system.*
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.control.NetworkSummary
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First Gaia settings surface: read status here, use firmware for unported operations. */
@Composable
fun GaiaSettingsScreen(state: Overlay.GaiaSettings, vm: HomeViewModel, onOpen: (Intent) -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<GaiaSystemInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) {
        val result = withContext(Dispatchers.IO) { runCatching { GaiaSystemInfo.read(context) } }
        snapshot = result.getOrNull()
        failed = result.isFailure
        vm.refreshNetwork()
    }
    fun page(name: String) { vm.overlay = Overlay.GaiaSettings(name) }
    fun back() {
        vm.overlay = if (state.page == "main") Overlay.ControlCenter() else Overlay.GaiaSettings()
    }
    fun open(intents: List<Intent>) {
        vm.whenUnlocked {
            LaunchFallback.arm(intents.drop(1))
            intents.firstOrNull()?.let(onOpen)
        }
    }
    BackHandler { back() }
    val first = remember(state.page) { FocusRequester() }
    LaunchedEffect(state.page) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val title = when (state.page) {
        "network" -> R.string.cc_network
        "bluetooth" -> R.string.cc_bluetooth
        "apps" -> R.string.gs_apps
        "system" -> R.string.gs_system
        else -> R.string.gs_title
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(850.dp).height(456.dp).liquidGlass(28.dp).padding(26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back), first = first,
                    modifier = Modifier.width(130.dp), onClick = { back() })
                Spacer(Modifier.width(20.dp))
                BasicText(stringResource(title), style = Type.LargeTitle)
            }
            Spacer(Modifier.height(16.dp))
            if (state.page == "apps") {
                if (snapshot == null) BasicText(stringResource(if (failed) R.string.unavailable else R.string.tasks_loading), style = Type.Body)
                val list = snapshot?.apps.orEmpty()
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val selected = remember(state.selectedApp) { FocusRequester() }
                LaunchedEffect(list, state.selectedApp) {
                    val index = list.indexOfFirst { it.pkg == state.selectedApp }
                    if (index >= 0) {
                        listState.scrollToItem(index)
                        withFrameNanos { }
                        runCatching { selected.requestFocus() }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list, key = { it.pkg }) { app ->
                        SettingRow(Glyph.Bag, app.name,
                            detail = if (app.manageable) app.pkg else stringResource(R.string.gs_protected),
                            first = selected.takeIf { app.pkg == state.selectedApp },
                            onClick = { vm.overlay = Overlay.AppInfo(app.pkg, fromSettings = true) })
                    }
                }
            } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.page) {
                    "network" -> {
                        NetworkSummary(vm.network)
                        InfoRow(stringResource(R.string.gs_lan), snapshot?.lan ?: stringResource(R.string.net_offline))
                        BasicText(stringResource(R.string.gs_network_hint), style = Type.Caption)
                        SettingRow(Glyph.Wifi, stringResource(R.string.gs_connect_wifi), detail = stringResource(R.string.gs_firmware_page), onClick = { open(SystemProbe.networkSettingsIntents(context)) })
                        SettingRow(Glyph.Ethernet, stringResource(R.string.net_ethernet), detail = stringResource(R.string.gs_firmware_page), onClick = { open(SystemProbe.networkSettingsIntents(context, ethernet = true)) })
                        val vpn = remember { context.packageManager.getLeanbackLaunchIntentForPackage("com.wireguard.android") ?: context.packageManager.getLaunchIntentForPackage("com.wireguard.android") }
                        if (vpn != null) SettingRow(Glyph.Shield, stringResource(R.string.gs_open_vpn), onClick = { open(listOf(vpn)) })
                    }
                    "bluetooth" -> {
                        InfoRow(stringResource(R.string.cc_bluetooth), stringResource(when(snapshot?.bluetooth) { true -> R.string.perm_on; false -> R.string.perm_off; null -> R.string.unavailable }))
                        BasicText(stringResource(R.string.gs_bluetooth_hint), style = Type.Body)
                        SettingRow(Glyph.Bluetooth, stringResource(R.string.gs_devices), detail = stringResource(R.string.gs_firmware_page), onClick = { open(SystemProbe.bluetoothSettingsIntents(context)) })
                    }
                    "system" -> {
                        val info = snapshot
                        if (info == null) BasicText(stringResource(if (failed) R.string.unavailable else R.string.tasks_loading), style = Type.Body)
                        else {
                            InfoRow(stringResource(R.string.gs_model), info.model)
                            InfoRow("Android", info.android)
                            InfoRow("Aerial Gaia", info.version)
                            InfoRow(stringResource(R.string.gs_memory), stringResource(R.string.gs_free_total, Formatter.formatFileSize(context, info.memoryAvailable), Formatter.formatFileSize(context, info.memoryTotal)))
                            InfoRow(stringResource(R.string.gs_storage), stringResource(R.string.gs_free_total, Formatter.formatFileSize(context, info.storageAvailable), Formatter.formatFileSize(context, info.storageTotal)))
                        }
                        SettingRow(Glyph.Gear, stringResource(R.string.gs_firmware), onClick = { open(listOf(SystemProbe.settingsIntent(context))) })
                    }
                    else -> {
                        SettingRow(Glyph.Wifi, stringResource(R.string.cc_network), detail = stringResource(R.string.gs_network_detail), onClick = { page("network") })
                        SettingRow(Glyph.Bluetooth, stringResource(R.string.cc_bluetooth), detail = stringResource(R.string.gs_bluetooth_detail), onClick = { page("bluetooth") })
                        SettingRow(Glyph.Bag, stringResource(R.string.gs_apps), detail = stringResource(R.string.gs_apps_detail), onClick = { page("apps") })
                        SettingRow(Glyph.Info, stringResource(R.string.gs_system), detail = stringResource(R.string.gs_system_detail), onClick = { page("system") })
                        SettingRow(Glyph.Gear, stringResource(R.string.gs_firmware), detail = stringResource(R.string.gs_firmware_detail), onClick = { open(listOf(SystemProbe.settingsIntent(context))) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(glyph: Glyph, title: String, detail: String? = null, first: FocusRequester? = null, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = first, focusedScale = 1.02f, modifier = modifier) { state ->
        val foreground = if (state.focused) Palette.OnFocus else Palette.Label
        Row(Modifier.fillMaxWidth().background(if (state.focused) Palette.Focus else Palette.Fill, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, foreground, 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Type.Body.copy(color = foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) BasicText(detail, style = Type.Caption.copy(color = foreground.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = Type.Body, modifier = Modifier.weight(1f))
        BasicText(value, style = Type.Body, modifier = Modifier.weight(1.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
