package app.orchard.tv.tasks

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.orchard.tv.ui.components.Pressable
import android.content.ComponentName
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import app.orchard.tv.wallpaper.Wallpapers
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.TileArtFactory
import app.orchard.tv.ui.home.AppTile
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.LocalReducedMotion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.theme.canvasDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Experimental switcher; deliberately independent from HOME and Direct Boot. */
class TaskSwitcherActivity : ComponentActivity() {
    private data class Task(val id: Int, val app: AppEntry, val closable: Boolean)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(canvasDensity(resources.displayMetrics)),
                LocalReducedMotion provides (Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f),
            ) {
                var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
                var status by remember { mutableIntStateOf(R.string.tasks_loading) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val first = remember { FocusRequester() }
                val infoFocus = remember { FocusRequester() }
                var showHelp by remember { mutableStateOf(false) }
                var closeError by remember { mutableStateOf(false) }
                BackHandler(showHelp || closeError) { showHelp = false; closeError = false }
                fun closeTask(task: Task) {
                    if (busy) return
                    if (!task.closable) { showHelp = true; return }
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { TaskBridgeClient.request(3, task.id) } }
                            .onSuccess { tasks = tasks.filterNot { it.id == task.id } }
                            .onFailure { closeError = true }
                        busy = false
                    }
                }
                LaunchedEffect(Unit) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val rows = JSONArray(TaskBridgeClient.request(1))
                            // Reuse Home artwork, including custom covers, without loading a second catalog.
                            val artwork = AppCatalog.loaded.associateBy { it.packageName }
                            (0 until rows.length()).mapNotNull { index ->
                                val row = rows.getJSONObject(index)
                                runCatching {
                                    val info = packageManager.getApplicationInfo(row.getString("package"), 0)
                                    val app = artwork[info.packageName] ?: AppEntry(
                                        packageName = info.packageName,
                                        label = info.loadLabel(packageManager).toString(),
                                        component = ComponentName(info.packageName, ""),
                                        leanback = false,
                                        removable = false,
                                        art = TileArtFactory.fromIcon(info.loadIcon(packageManager), null),
                                    )
                                    Task(row.getInt("id"), app, row.optBoolean("closable", false))
                                }.getOrNull()
                            }
                        }
                    }.onSuccess { tasks = it; status = R.string.tasks_empty }
                        .onFailure { status = R.string.tasks_unavailable }
                }
                LaunchedEffect(tasks, status) { if (tasks.isNotEmpty()) first.requestFocus() else if (status != R.string.tasks_loading) infoFocus.requestFocus() }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color(0xFF1C2534), Color(0xFF10151F), Color(0xFF090C12),
                )))) {
                    // Share the launcher's prepared still image; never start a second video player.
                    Wallpapers.backdrop?.let { wallpaper ->
                        Image(
                            bitmap = wallpaper,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    }
                    if (showHelp) Column(Modifier.align(Alignment.TopStart).padding(start = 58.dp, top = 54.dp)) {
                        BasicText(stringResource(R.string.tasks_title), style = Type.LargeTitle.copy(fontSize = 30.sp))
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 42.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (showHelp || closeError) BasicText(
                            stringResource(if (closeError) R.string.tasks_close_failed else R.string.tasks_help),
                            style = Type.Body.copy(color = Palette.Label, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        )
                        val infoLabel = stringResource(R.string.tasks_info)
                        Pressable(
                            focusRequester = infoFocus,
                            onClick = { showHelp = !showHelp; closeError = false },
                            modifier = Modifier.semantics { contentDescription = infoLabel },
                        ) { state ->
                            Box(
                                Modifier.size(30.dp)
                                    .background(if (state.focused) Palette.Focus else Color.Black.copy(alpha = 0.22f), CircleShape)
                                    .border(1.dp, if (state.focused) Palette.Focus else Palette.SecondaryLabel, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText("i", style = Type.Title.copy(color = if (state.focused) Palette.OnFocus else Palette.Label))
                            }
                        }
                    }
                    if (tasks.isEmpty()) {
                        BasicText(stringResource(status), modifier = Modifier.align(Alignment.Center), style = Type.Title)
                    } else {
                        LazyRow(
                            Modifier.fillMaxWidth().align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 58.dp, vertical = 36.dp),
                        ) {
                            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                                val requester = if (index == 0) first else remember(task.id) { FocusRequester() }
                                // Use Home's tile itself: focus style, lift, rim, shadow and edge nudge
                                // must follow the dock settings rather than a second approximation.
                                Box(Modifier.size(200.dp, 170.dp).onPreviewKeyEvent { event ->
                                    when (event.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) closeTask(task)
                                            true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (event.type == KeyEventType.KeyDown) infoFocus.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }) {
                                    AppTile(
                                        app = task.app,
                                        width = 200.dp,
                                        height = 112.5.dp,
                                        focusRequester = requester,
                                        enabled = !busy,
                                        moving = false,
                                        appearDelayMs = 0,
                                        onFocused = {},
                                        onLongPress = {},
                                        rowEnd = index == tasks.lastIndex,
                                        onClick = {
                                            if (!busy) {
                                                busy = true
                                                scope.launch {
                                                    runCatching { withContext(Dispatchers.IO) { TaskBridgeClient.request(2, task.id) } }
                                                        .onSuccess { finish() }
                                                        .onFailure { status = R.string.tasks_unavailable; tasks = emptyList() }
                                                    busy = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
