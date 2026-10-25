package app.orchard.tv.ui.settings

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
import app.orchard.tv.ui.components.PackageIcon
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.control.NetworkSummary
import app.orchard.tv.ui.control.VolumeRow
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Native system preferences in the same bottom-sheet surface as Personalize. */
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
        vm.refreshVolume()
    }
    fun page(name: String) { vm.overlay = Overlay.GaiaSettings(name) }
    fun back() {
        vm.overlay = if (state.page == "main") Overlay.ControlCenter() else if (state.page == "outputs") Overlay.GaiaSettings("sound") else if (state.page in listOf("datetime", "keyboard", "display", "captions")) Overlay.GaiaSettings("advanced") else Overlay.GaiaSettings(selectedSection = state.page)
    }
    fun open(intents: List<Intent>) {
        vm.whenUnlocked {
            LaunchFallback.arm(intents.drop(1))
            intents.firstOrNull()?.let(onOpen)
        }
    }
    BackHandler { back() }
    val first = remember(state.page) { FocusRequester() }
    LaunchedEffect(state.page) { if (state.page != "main") { withFrameNanos { }; runCatching { first.requestFocus() } } }
    val title = when (state.page) {
        "network" -> R.string.cc_network
        "picture" -> R.string.gs_av_picture
        "outputs" -> R.string.gs_av_outputs
        "sound" -> R.string.gs_sound
        "bluetooth" -> R.string.cc_bluetooth
        "apps" -> R.string.gs_apps
        "datetime" -> R.string.gs_date
        "keyboard" -> R.string.gs_keyboard
        "captions" -> R.string.gs_captions
        "display" -> R.string.gs_display
        "advanced" -> R.string.gs_preferences
        "system" -> R.string.gs_system
        else -> R.string.gs_title
    }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(380, easing = Motion.Drawer)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().dismissOnTap { back() }.background(Color.Black.copy(alpha = 0.25f)))
        Column(Modifier.fillMaxWidth().height(360.dp)
            .graphicsLayer { translationY = (1f - entrance.value) * 120.dp.toPx(); alpha = entrance.value }
            .consumeTaps().liquidGlass(30.dp, bottomCorners = false)
            .padding(start = 40.dp, end = 32.dp, top = 24.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back), first = first,
                    modifier = Modifier.width(130.dp), onClick = { back() })
                Spacer(Modifier.width(20.dp))
                BasicText(stringResource(title), style = Type.LargeTitle)
            }
            Spacer(Modifier.height(16.dp))
            if (state.page == "main") {
                SettingsTiles(state.selectedSection, Modifier.weight(1f)) { section ->
                    if (section == "personalize") vm.overlay = Overlay.Wallpaper else page(section)
                }
            } else if (state.page == "apps") {
                if (snapshot == null) BasicText(stringResource(if (failed) R.string.unavailable else R.string.tasks_loading), style = Type.Body)
                SettingRow(Glyph.Gear, stringResource(if (vm.settingsShowSystemApps) R.string.gs_hide_system else R.string.gs_show_system), onClick = {
                    vm.settingsShowSystemApps = !vm.settingsShowSystemApps
                })
                Spacer(Modifier.height(8.dp))
                val list = snapshot?.apps.orEmpty().filter { vm.settingsShowSystemApps || !it.system }
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
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(list, key = { it.pkg }) { app ->
                        SettingRow(Glyph.Bag, app.name, packageName = app.pkg,
                            detail = if (app.manageable) app.pkg else stringResource(R.string.gs_protected),
                            first = selected.takeIf { app.pkg == state.selectedApp },
                            onClick = { vm.overlay = Overlay.AppInfo(app.pkg, fromSettings = true) })
                    }
                }
            } else key(state.page) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (state.page) {
                        "network" -> {
                            NetworkSummary(vm.network)
                            InfoRow(stringResource(R.string.gs_lan), snapshot?.lan ?: stringResource(if (snapshot == null && !failed) R.string.tasks_loading else R.string.net_offline))
                            ConnectivityPreferences(false, vm, revision)
                            val vpn = remember { context.packageManager.getLeanbackLaunchIntentForPackage("com.wireguard.android") ?: context.packageManager.getLaunchIntentForPackage("com.wireguard.android") }
                            if (vpn != null) SettingRow(Glyph.Shield, stringResource(R.string.gs_open_vpn), onClick = { open(listOf(vpn)) })
                        }
                        "picture", "outputs" -> AvPreferences(state.page == "picture", revision, vm)
                        "sound" -> {
                            BasicText(stringResource(R.string.cc_volume), style = Type.Headline)
                            VolumeRow(vm)
                            BasicText(stringResource(R.string.gs_volume_hint), style = Type.Caption)
                            SettingRow(if (vm.volume.muted) Glyph.SpeakerMuted else Glyph.Speaker,
                                stringResource(if (vm.volume.muted) R.string.gs_unmute else R.string.gs_mute),
                                onClick = { vm.whenUnlocked { vm.toggleMute() } })
                            SettingRow(Glyph.Bluetooth, stringResource(R.string.gs_devices), onClick = { page("bluetooth") })
                            SettingRow(Glyph.Speaker, stringResource(R.string.gs_av_outputs), onClick = { page("outputs") })
                            DevicePreferences("sound", vm, revision)
                        }
                        "bluetooth" -> {
                            ConnectivityPreferences(true, vm, revision)
                        }
                        "advanced" -> {
                            InfoRow(stringResource(R.string.gs_date), android.text.format.DateFormat.getDateFormat(context).format(java.util.Date()))
                            InfoRow(stringResource(R.string.gs_timezone), java.util.TimeZone.getDefault().id)
                            InfoRow(stringResource(R.string.gs_language), java.util.Locale.getDefault().displayName)
                            SettingRow(Glyph.Gear, stringResource(R.string.gs_date), onClick = { page("datetime") })
                            SettingRow(Glyph.Gear, stringResource(R.string.gs_keyboard), onClick = { page("keyboard") })
                            SettingRow(Glyph.Gear, stringResource(R.string.gs_captions), onClick = { page("captions") })
                            SettingRow(Glyph.Gear, stringResource(R.string.gs_display), onClick = { page("display") })
                        }
                        "datetime", "keyboard", "display", "captions" -> DevicePreferences(state.page, vm, revision)

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
                        }
                        else -> {
                            SettingRow(Glyph.Wifi, stringResource(R.string.cc_network), detail = stringResource(R.string.gs_network_detail), onClick = { page("network") })
                            SettingRow(Glyph.Bluetooth, stringResource(R.string.cc_bluetooth), detail = stringResource(R.string.gs_bluetooth_detail), onClick = { page("bluetooth") })
                            SettingRow(Glyph.Speaker, stringResource(R.string.gs_sound), detail = stringResource(R.string.gs_sound_detail), onClick = { page("sound") })
                            SettingRow(Glyph.Bag, stringResource(R.string.gs_apps), detail = stringResource(R.string.gs_apps_detail), onClick = { page("apps") })
                            SettingRow(Glyph.Gear, stringResource(R.string.gs_preferences), detail = stringResource(R.string.gs_preferences_detail), onClick = { page("advanced") })
                            SettingRow(Glyph.Info, stringResource(R.string.gs_system), detail = stringResource(R.string.gs_system_detail), onClick = { page("system") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingRow(glyph: Glyph, title: String, detail: String? = null, first: FocusRequester? = null, modifier: Modifier = Modifier.fillMaxWidth(), packageName: String? = null, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = first, focusedScale = 1.01f, modifier = modifier) { state ->
        val foreground = if (state.focused) Palette.OnFocus else Palette.Label
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .background(if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.05f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (packageName != null) PackageIcon(packageName) else GlyphIcon(glyph, foreground, 22.dp)
            Spacer(Modifier.width(14.dp))
            BasicText(title, style = Type.Body.copy(color = foreground), modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Spacer(Modifier.width(20.dp))
                BasicText(detail, style = Type.Caption.copy(color = foreground.copy(alpha = 0.7f)),
                    modifier = Modifier.widthIn(max = 360.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
