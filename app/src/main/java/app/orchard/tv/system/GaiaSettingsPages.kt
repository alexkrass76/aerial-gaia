package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.orchard.tv.R

/** An allowlist of settings pages, never arbitrary exported activities or service commands. */
internal object GaiaSettingsPages {
    data class Page(val title: Int, val actions: List<String>)
    data class Available(val title: Int, val intent: Intent)

    private val pages = listOf(
        Page(R.string.gs_date, listOf("android.settings.DATE_SETTINGS")),
        Page(R.string.gs_language, listOf("android.settings.LOCALE_SETTINGS")),
        Page(R.string.gs_keyboard, listOf("com.cvte.intent.action.InputMethodLanguage", "android.settings.INPUT_METHOD_SETTINGS")),
        Page(R.string.gs_accessibility, listOf("com.cvte.intent.action.ACCESSIBILITY_SETTING", "android.settings.ACCESSIBILITY_SETTINGS")),
        Page(R.string.gs_accounts, listOf("com.cvte.intent.action.ACCOUNT_SETTINGS", "android.settings.SYNC_SETTINGS")),
        Page(R.string.gs_display, listOf("android.settings.DISPLAY_SETTINGS")),
        Page(R.string.gs_storage, listOf("android.settings.INTERNAL_STORAGE_SETTINGS")),
        Page(R.string.gs_notifications, listOf("android.settings.NOTIFICATION_SETTINGS")),
        Page(R.string.gs_general, listOf("android.settings.GENERAL_SETTINGS")),
    )

    fun available(context: Context): List<Available> = pages.mapNotNull { page ->
        page.actions.firstNotNullOfOrNull { action -> resolve(context, action) }
            ?.let { Available(page.title, it) }
    }

    /** Resolve in the app UID: shell access does not imply launcher access. Pin the result. */
    internal fun resolve(context: Context, action: String): Intent? = runCatching {
        val pm = context.packageManager
        val probe = Intent(action).apply {
            if (action.startsWith("com.cvte.")) setPackage("com.cvte.settings")
        }
        pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence().map { it.activityInfo }
            .filter { it.exported && it.enabled && it.applicationInfo.enabled }
            .filter { it.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .filter { it.permission.isNullOrEmpty() || androidx.core.content.ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED }
            .sortedBy { if (it.packageName == "com.cvte.settings") 0 else 1 }
            .firstOrNull()?.let {
                Intent(probe).setComponent(ComponentName(it.packageName, it.name))
            }
    }.getOrNull()
}
