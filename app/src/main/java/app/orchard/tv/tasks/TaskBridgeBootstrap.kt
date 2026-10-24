package app.orchard.tv.tasks

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Opt-in, post-unlock bootstrap. No system settings, root requests or remote endpoints. */
internal object TaskBridgeBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = AtomicBoolean(false)
    @Volatile var status = "idle"
        private set
    private const val DIR = "/data/local/tmp/aerial-task-bridge"

    fun ensure(context: Context) {
        val app = context.applicationContext
        if (app.packageName != "app.orchard.tv.gaia" || Build.VERSION.SDK_INT < 31) return
        if (!app.getSystemService(UserManager::class.java).isUserUnlocked) return
        if (!app.getSharedPreferences("task_bridge", Context.MODE_PRIVATE).getBoolean("autostart", false)) return
        if (TaskBridgeClient.binder?.isBinderAlive == true || !starting.compareAndSet(false, true)) return
        scope.launch {
            try {
                for (pause in listOf(0L, 1000L, 2000L, 4000L, 8000L, 15000L, 30000L, 60000L)) {
                    delay(pause)
                    if (!app.getSharedPreferences("task_bridge", Context.MODE_PRIVATE).getBoolean("autostart", false)) return@launch
                    if (TaskBridgeClient.binder?.isBinderAlive == true) return@launch
                    try {
                        status = "connecting"
                        launchHelper(app)
                        repeat(25) {
                            if (TaskBridgeClient.binder?.isBinderAlive == true) {
                                status = "attached"
                                Log.i("AerialTasks", "Local ADB bootstrap connected")
                                return@launch
                            }
                            delay(200)
                        }
                    } catch (e: Exception) {
                        status = "unavailable: ${e.message}"
                        Log.w("AerialTasks", "Local bootstrap unavailable: ${e.message}")
                        if (e.message?.contains("requires authorization") == true) return@launch
                    }
                }
            } finally { starting.set(false) }
        }
    }

    private fun launchHelper(app: Context) {
        val dex = app.assets.open("task-bridge/classes.dex").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(dex).joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = "$DIR/bridge-$digest.dex"
        val ready = LocalAdbTransport.connect().use {
            it.shell("test \"\$(id -u)\" = 2000 && umask 077 && mkdir -p $DIR && chmod 700 $DIR && echo AERIAL_READY")
        }
        check(ready.contains("AERIAL_READY")) { "ADB shell UID unavailable" }
        status = "uploading"
        LocalAdbTransport.connect().use { it.push("$file.tmp", dex) }
        val uploaded = LocalAdbTransport.connect().use { it.shell("sha256sum $file.tmp") }
        check(uploaded.trim().substringBefore(' ') == digest) { "Helper checksum mismatch" }
        status = "launching"
        val started = LocalAdbTransport.connect().use {
            it.shell("umask 077; mv $file.tmp $file && chmod 600 $file && " +
                "(CLASSPATH=$file nohup setsid app_process /system/bin gaia.bridge.TaskBridge " +
                "'name=Viettronics Keyboard:172' 'name=ir_keypad:102' 'name=ir_keypad1:102' 'name=input_btrcu:102' " +
                "> $DIR/bootstrap.log 2>&1 < /dev/null &); sleep 1; echo AERIAL_STARTED")
        }
        check(started.trim() == "AERIAL_STARTED") { "Helper start failed: ${started.take(200)}" }
        status = "waiting for Binder"
    }
}
