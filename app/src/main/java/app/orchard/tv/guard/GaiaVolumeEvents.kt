package app.orchard.tv.guard

import android.content.*
import android.os.*
import dalvik.system.PathClassLoader

/** Passive subscription to the installed firmware. No keys or volume writes. Main-thread lifecycle. */
internal class GaiaVolumeEvents(private val context: Context, private val changed: () -> Unit, private val unsupported: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val api = "com.cvte.tv.api.aidl."
    var ready = false
        private set
    private var bound = false
    private var manager: Any? = null
    private var callback: Any? = null
    private var loader: ClassLoader? = null
    private fun type(name: String) = Class.forName(api + name, false, requireNotNull(loader))
    private val dispatch = Runnable { if (bound) changed() }
    private val receiver = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(api + "INotifyListener"); return true }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            check(getCallingUid() == 1000) { "System required" }
            data.enforceInterface(api + "INotifyListener")
            val action = data.readString()
            val payload = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
            if (action == "notifySoundSpeakerOutChange" || action == "notifySoundSpeakerOutMuteChange") {
                // Collapse duplicate notifications without delaying a held remote indefinitely.
                handler.removeCallbacks(dispatch)
                handler.postDelayed(dispatch, 25)
            }
            if (action != null && (action == "notifySoundArcAmpVolumeChange" || action == "notifySoundArcAmpMuteChange" ||
                    (action.endsWith("Remaining") && (payload?.getInt("RemainingSeconds", -1) ?: -1) >= 0) || action == "notifySystemInputSourceChange")) {
                handler.post { android.util.Log.i("GaiaVolume", "Restoring stock UI for " + action); unsupported() }
            }
            reply?.writeNoException()
            return true
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!bound) return
            runCatching {
                check(service.interfaceDescriptor == api + "ITvApiManager")
                manager = type("ITvApiManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, service)
                callback = type("INotifyListener\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, receiver)
                type("ITvApiManager").getMethod("addNotifyListener", type("INotifyListener")).invoke(manager, callback)
                ready = true
            }.onFailure { android.util.Log.w("GaiaVolume", "Cannot subscribe", it); close() }
        }
        override fun onServiceDisconnected(name: ComponentName) { ready = false; manager = null; callback = null }
        override fun onBindingDied(name: ComponentName) { close(); handler.postDelayed({ if (GaiaVolumeOverlay.enabled(context)) start() }, 2000) }
        override fun onNullBinding(name: ComponentName) { close() }
    }
    fun start() {
        if (bound || !context.getSystemService(UserManager::class.java).isUserUnlocked) return
        runCatching {
            loader = PathClassLoader("/system/framework/cn.cvte.tv.api.jar", context.classLoader)
            bound = context.bindService(Intent("com.cvte.tv.api.TV_API_SERVICE").setComponent(
                ComponentName("com.cvte.tv.api.impl", "com.cvte.tv.api.TvApiService")), connection, Context.BIND_AUTO_CREATE)
        }.onFailure { android.util.Log.w("GaiaVolume", "Cannot bind", it) }
    }
    fun close() {
        ready = false
        handler.removeCallbacksAndMessages(null)
        if (manager != null && callback != null) runCatching {
            type("ITvApiManager").getMethod("removeNotifyListener", type("INotifyListener")).invoke(manager, callback)
        }
        if (bound) runCatching { context.unbindService(connection) }
        bound = false; manager = null; callback = null
    }
}
