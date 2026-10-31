package app.orchard.tv.channels

import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.orchard.tv.shelf.ShelfImages
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import app.orchard.tv.R
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.*
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.PrintWriter

class ChannelsActivity : ComponentActivity() {
    private var status = "not_loaded"
    private var count = 0
    private var groupCount = 0
    private var artworkCount = 0
    private var programCount = 0
    private var epgStatus = "not_loaded"
    private val signal = CancellationSignal()
    override fun onDestroy() { signal.cancel(); super.onDestroy() }
    override fun dump(prefix: String, fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        super.dump(prefix, fd, writer, args)
        writer.println("AerialChannels status=$status channels=$count groups=$groupCount artwork=$artworkCount programs=$programCount epg=$epgStatus")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wallpapers.init(this)
        ShelfImages.init(this)
        val prefs = getSharedPreferences("yaos_channels", MODE_PRIVATE)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(canvasDensity(resources.displayMetrics))) {
                var channels by remember { mutableStateOf<List<YaosChannels.Channel>>(emptyList()) }
                var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
                var selected by remember { mutableStateOf("all") }
                var loading by remember { mutableStateOf(true) }
                var failed by remember { mutableStateOf(false) }
                var playFailed by remember { mutableStateOf(false) }
                var programs by remember { mutableStateOf<Map<Long, YaosChannels.Program>>(emptyMap()) }
                var reload by remember { mutableIntStateOf(0) }
                val first = remember { FocusRequester() }
                BackHandler { finish() }
                LaunchedEffect(Unit) { first.requestFocus() }
                LaunchedEffect(reload) {
                    loading = true; failed = false; status = "loading"
                    runCatching { withContext(Dispatchers.IO) { YaosChannels.load(this@ChannelsActivity, signal) } }
                        .onSuccess {
                            channels = it; count = it.size; groupCount = it.flatMap { c -> c.groups }.distinctBy { g -> g.id }.size
                            artworkCount = it.count { c -> c.artwork != null }
                            status = if (it.isEmpty()) "empty" else "ready"
                        }.onFailure { failed = true; status = it.javaClass.simpleName }
                    loading = false
                }
                LaunchedEffect(reload) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            try {
                                programs = withContext(Dispatchers.IO) { YaosChannels.programs(this@ChannelsActivity, signal) }
                                programCount = programs.size; epgStatus = "ready"
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { programs = emptyMap(); programCount = 0; epgStatus = e.javaClass.simpleName }
                            delay(60_000)
                        }
                    }
                }
                val groups = remember(channels) { channels.flatMap { it.groups }.distinctBy { it.id }.sortedBy { it.order } }
                val visible = remember(channels, favorites, selected) {
                    channels.filter { selected == "all" || (selected == "favorites" && it.key in favorites) || it.groups.any { g -> "group:${g.id}" == selected } }
                }
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    Wallpapers.backdrop?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f)))
                    Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ChannelButton(stringResource(R.string.channels_back), Modifier.width(130.dp), first) { finish() }
                            BasicText(stringResource(R.string.yaos_channels), style = Type.LargeTitle, modifier = Modifier.weight(1f))
                            ChannelButton(stringResource(R.string.channels_refresh), Modifier.width(140.dp)) { if (!loading) reload++ }
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            LazyColumn(Modifier.width(205.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(5.dp)) {
                                item { ChannelButton(stringResource(R.string.channels_all), selected = selected == "all") { selected = "all" } }
                                item { ChannelButton(stringResource(R.string.channels_favorites), selected = selected == "favorites") { selected = "favorites" } }
                                items(groups, key = { it.id }) { g -> ChannelButton(g.name, selected = selected == "group:${g.id}") { selected = "group:${g.id}" } }
                            }
                            Column(Modifier.weight(1f)) {
                                if (loading || failed || visible.isEmpty()) BasicText(stringResource(when {
                                    loading -> R.string.channels_loading
                                    failed -> R.string.channels_error
                                    else -> R.string.channels_empty
                                }), style = Type.Body)
                                else LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(visible, key = { it.key }) { channel ->
                                        ChannelCard(channel, programs[channel.id], channel.key in favorites,
                                            onPlay = {
                                                playFailed = runCatching { startActivity(YaosChannels.playback(channel, selected.removePrefix("group:").takeIf { selected.startsWith("group:") })) }.isFailure
                                            }, onFavorite = {
                                                favorites = if (channel.key in favorites) favorites - channel.key else favorites + channel.key
                                                prefs.edit().putStringSet("favorites", favorites).apply()
                                                if (selected == "favorites") first.requestFocus()
                                            })
                                    }
                                }
                                if (playFailed) BasicText(stringResource(R.string.channels_play_error), style = Type.Body)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), requester: FocusRequester? = null, selected: Boolean = false, onClick: () -> Unit) {
    Pressable(onClick, modifier, focusRequester = requester, focusedScale = 1.025f) { state ->
        Box(Modifier.fillMaxWidth().background(if (state.focused) Palette.Focus else Color.White.copy(alpha = if (selected) .24f else .08f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            BasicText(label, style = Type.Body.copy(color = if (state.focused) Palette.OnFocus else Palette.Label), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelCard(channel: YaosChannels.Channel, program: YaosChannels.Program?, favorite: Boolean,
                        onPlay: () -> Unit, onFavorite: () -> Unit) {
    val artwork by produceState<android.graphics.Bitmap?>(null, channel.artwork) {
        value = channel.artwork?.let { ShelfImages.card(it, 560, 315, fit = true) }
    }
    var menu by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val favoriteLabel = stringResource(if (favorite) R.string.channels_remove_favorite else R.string.channels_add_favorite)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Pressable(onPlay, Modifier.fillMaxWidth(), focusedScale = 1.035f, onLongClick = { menu = true }) { state ->
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape)
                .background(if (state.focused) Palette.Focus else Color.White.copy(alpha = .10f))) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black.copy(alpha = .30f)), contentAlignment = Alignment.Center) {
                    if (artwork != null) Image(artwork!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else BasicText(channel.name, style = Type.Body, maxLines = 2, modifier = Modifier.padding(16.dp))
                    if (favorite) BasicText("★", style = Type.Body.copy(color = Color(0xFFFFD66B)),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                val ink = if (state.focused) Palette.OnFocus else Palette.Label
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(channel.name, style = Type.Body.copy(color = ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(program?.title ?: stringResource(R.string.channels_no_program),
                        style = Type.Body.copy(color = ink.copy(alpha = .75f), fontSize = 14.sp),
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    if (program != null && program.description.isNotBlank()) BasicText(program.description,
                        style = Type.Body.copy(color = ink.copy(alpha = .60f), fontSize = 12.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (menu) Dialog(onDismissRequest = { menu = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { menuFocus.requestFocus() }
        Column(Modifier.width(340.dp).background(Palette.Background, RoundedCornerShape(22.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText(channel.name, style = Type.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ChannelButton(favoriteLabel, requester = menuFocus) { menu = false; onFavorite() }
            ChannelButton(stringResource(R.string.channels_back)) { menu = false }
        }
    }
}
