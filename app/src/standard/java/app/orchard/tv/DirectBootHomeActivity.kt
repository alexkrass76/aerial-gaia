package app.orchard.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log

/** Minimal HOME available before CE storage unlocks. Never initializes Compose or app settings.
 * GaiaOS/CVTE needs a drawable HOME in Direct Boot to finish booting and unlock user data.
 */
class DirectBootHomeActivity : Activity() {
    private var listening = false
    private var openingHome = false
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED) openHomeIfUnlocked()
        }
    }

    private fun isUnlocked(): Boolean = Build.VERSION.SDK_INT < 24 ||
        (getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "created; userUnlocked=${isUnlocked()}")
        // Resources are APK-backed; no wallpaper, preferences, files, PIN or network access.
        setContentView(DirectBootView(this))
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 24) {
            val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(unlocked, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(unlocked, filter)
            }
            listening = true
        }
        // Register first, then check: unlock between these steps must not be lost.
        openHomeIfUnlocked()
    }

    private fun openHomeIfUnlocked() {
        if (openingHome || !isUnlocked()) return
        openingHome = true
        Log.i(TAG, "user unlocked; opening MainActivity")
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    override fun onStop() {
        if (listening) {
            unregisterReceiver(unlocked)
            listening = false
        }
        super.onStop()
    }

    companion object { private const val TAG = "AerialDirectBoot" }
}
