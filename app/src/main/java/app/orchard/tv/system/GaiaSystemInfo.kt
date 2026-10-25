package app.orchard.tv.system

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import java.text.Collator

/** Read-only snapshot, loaded off the UI thread and refreshed when settings resumes. */
internal data class GaiaSystemInfo(
    val model: String, val android: String, val version: String,
    val memoryAvailable: Long, val memoryTotal: Long,
    val storageAvailable: Long, val storageTotal: Long,
    val lan: String?, val bluetooth: Boolean?, val apps: List<SettingsApp>,
) {
    companion object {
        fun read(context: Context): GaiaSystemInfo {
            val pm = context.packageManager
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
            val storage = StatFs(context.filesDir.path)
            val collator = Collator.getInstance()
            val apps = pm.getInstalledApplications(0).filter { it.enabled }.map {
                SettingsApp(it.packageName, runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName),
                    AdbShell.canManage(context, it.packageName),
                    it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }.sortedWith { a, b -> collator.compare(a.name, b.name) }
            return GaiaSystemInfo(Build.MODEL, Build.VERSION.RELEASE,
                pm.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                memory.availMem, memory.totalMem, storage.availableBytes, storage.totalBytes,
                runCatching { LanAddress.find(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager) }.getOrNull(),
                runCatching { Settings.Global.getInt(context.contentResolver, "bluetooth_on") == 1 }.getOrNull(), apps)
        }
    }
}
internal data class SettingsApp(val pkg: String, val name: String, val manageable: Boolean, val system: Boolean)
