package app.orchard.tv.tasks

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel

/** Shell-only bootstrap. The handle stays private to the launcher process. */
class TaskBridgeProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == 2000 || Binder.getCallingUid() == 0) { "Shell required" }
        if (method == "status") return Bundle().apply {
            putBoolean("autostart", requireNotNull(context).getSharedPreferences("task_bridge", 0).getBoolean("autostart", false))
            putBoolean("connected", TaskBridgeClient.binder?.isBinderAlive == true)
            putString("bootstrap", TaskBridgeBootstrap.status)
        }
        if (method == "set_autostart") {
            require(arg == "true" || arg == "false")
            val app = requireNotNull(context)
            val enabled = arg == "true"
            check(app.getSharedPreferences("task_bridge", 0).edit().putBoolean("autostart", enabled).commit())
            if (enabled) TaskBridgeBootstrap.ensure(app)
            return Bundle().apply { putBoolean("autostart", enabled) }
        }
        require(method == "attach")
        val candidate = requireNotNull(extras?.getBinder("bridge"))
        candidate.linkToDeath({
            if (TaskBridgeClient.binder === candidate) {
                TaskBridgeClient.binder = null
                context?.let(TaskBridgeBootstrap::ensure)
            }
        }, 0)
        TaskBridgeClient.binder = candidate
        return Bundle().apply { putBoolean("attached", true); putBinder("lifetime", object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (getCallingUid() != 2000 && getCallingUid() != 0) throw SecurityException("Shell required")
                data.enforceInterface("app.orchard.tv.gaia.home.v1")
                if (code != 1) return false
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    TaskBridgeClient.requestDoubleHome()
                }
                return true
            }
        }) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

internal object TaskBridgeClient {
    private val homeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingHomeAt: Long? = null
    var onDoubleHome: (() -> Unit)? = null
        set(value) {
            field = value
            if (value != null) homeHandler.postDelayed({ deliverPendingHome() }, 180)
        }

    fun requestDoubleHome() {
        pendingHomeAt = android.os.SystemClock.uptimeMillis()
        android.util.Log.i("AerialTasks", "double Home received; waiting for HOME transition")
        homeHandler.postDelayed({ deliverPendingHome() }, 180)
    }

    private fun deliverPendingHome() {
        val at = pendingHomeAt ?: return
        if (android.os.SystemClock.uptimeMillis() - at > 1000) {
            pendingHomeAt = null
            return
        }
        val listener = onDoubleHome ?: return
        pendingHomeAt = null
        listener()
    }
    @Volatile var binder: IBinder? = null
    fun request(code: Int, taskId: Int? = null): String? {
        val remote = binder ?: error("Bridge unavailable")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("app.orchard.tv.gaia.tasks.v1")
            if (taskId != null) data.writeInt(taskId)
            check(remote.transact(code, data, reply, 0))
            reply.readException()
            return if (code == 1) reply.readString() else null
        } finally { data.recycle(); reply.recycle() }
    }
}
