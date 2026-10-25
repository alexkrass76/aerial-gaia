package app.orchard.tv.system

import android.content.Context
import android.content.pm.ApplicationInfo
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.tasks.LocalAdbTransport
import java.io.IOException

/** Gaia app management uses the same bounded loopback transport as task bootstrap.
 * No authorization keys, ADB settings changes, remote endpoint or arbitrary command input. */
object AdbShell {
    class AdbException(message: String, cause: Throwable? = null) : IOException(message, cause)
    private val NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    enum class AppOp(val opString: String, val permission: String, val uidMode: Boolean) {
        MANAGE_EXTERNAL_STORAGE("android:manage_external_storage", "android.permission.MANAGE_EXTERNAL_STORAGE", true),
        SYSTEM_ALERT_WINDOW("android:system_alert_window", "android.permission.SYSTEM_ALERT_WINDOW", false),
        WRITE_SETTINGS("android:write_settings", "android.permission.WRITE_SETTINGS", false),
        REQUEST_INSTALL_PACKAGES("android:request_install_packages", "android.permission.REQUEST_INSTALL_PACKAGES", false),
    }

    enum class AppAction(val command: String) {
        FORCE_STOP("am force-stop"),
        CLEAR_DATA("pm clear"),
    }


    fun canManage(context: Context, pkg: String): Boolean = runCatching {
        AppManagementPolicy.allows(pkg) &&
            context.packageManager.getApplicationInfo(pkg, 0).flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
    }.getOrDefault(false)

    @Synchronized
    fun setPermission(context: Context, pkg: String, permission: String, granted: Boolean, onApproval: () -> Unit = {}): String {
        if (!NAME.matches(permission)) throw AdbException("Invalid permission")
        return run(context, pkg, "pm ${if (granted) "grant" else "revoke"} --user 0 $pkg $permission")
    }

    @Synchronized
    fun setAppOp(context: Context, pkg: String, op: AppOp, allowed: Boolean, onApproval: () -> Unit = {}): String {
        val uid = if (op.uidMode) "--uid " else ""
        return run(context, pkg, "appops set --user 0 $uid$pkg ${op.name} ${if (allowed) "allow" else "deny"}")
    }

    @Synchronized
    fun act(context: Context, pkg: String, action: AppAction, onApproval: () -> Unit = {}): String =
        run(context, pkg, "${action.command} --user 0 $pkg")

    // Gaia never creates a key. Disabling the preference blocks subsequent commands.
    fun revoke(context: Context) = Unit

    private fun run(context: Context, pkg: String, command: String): String {
        if (!AerialSettings.adbAccess || AerialSettings.locked) throw AdbException("App management is locked")
        if (!NAME.matches(pkg) || !canManage(context, pkg)) throw AdbException("Protected application")
        try {
            return LocalAdbTransport.connect().use { adb ->
                if (adb.shell("id -u").trim() != "2000") throw AdbException("Shell UID required")
                // Recheck after connection; disabling access must prevent queued changes.
                if (!AerialSettings.adbAccess || AerialSettings.locked) throw AdbException("App management is locked")
                val output = adb.shell(command + "; printf '\\nGAIA_EXIT:%s\\n' $? ")
                val code = output.substringAfterLast("GAIA_EXIT:", "missing").trim()
                val message = output.substringBeforeLast("GAIA_EXIT:").trim()
                if (code != "0") throw AdbException("Command failed: $message")
                message.takeUnless { it == "Success" }.orEmpty()
            }
        } catch (e: IOException) {
            throw AdbException("Local shell unavailable or operation refused", e)
        }
    }
}
