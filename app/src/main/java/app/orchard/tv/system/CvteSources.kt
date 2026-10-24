package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvInputInfo

/** Native CVTE TIF route, discovered from GaiaOS t950s_be30ae's own input picker.
 * Its sourceId is the Droidlogic hardware ID, not CVTE's EnumVendorInputSource ordinal.
 * Keep other platforms and non-HDMI inputs on their existing routes.
 */
internal object CvteSources {
    fun route(context: Context, info: TvInputInfo): Intent? {
        if (info.type != TvInputInfo.TYPE_HDMI ||
            !info.id.startsWith("com.droidlogic.tvinput/")) return null
        val sourceId = info.id.substringAfterLast("/HW", "").toIntOrNull() ?: return null
        if (sourceId !in 5..8) return null
        val intent = Intent("com.cvte.intent.ACTION_TIF_PLAYER")
            .setComponent(ComponentName("com.cvte.tv.api.impl",
                "com.cvte.tv.api.vendor.common.base.tif.TvSupportActivity"))
            .addCategory(Intent.CATEGORY_DEFAULT)
        val pm = context.packageManager
        val target = pm.resolveActivity(intent, 0)?.activityInfo ?: return null
        if (!target.exported || !target.enabled) return null
        val metrics = context.resources.displayMetrics
        return intent
            .putExtra("sourceId", sourceId)
            .putExtra("left", 0).putExtra("top", 0)
            .putExtra("width", metrics.widthPixels).putExtra("height", metrics.heightPixels)
            .putExtra("tune", true)
            .putExtra("toPackage", "com.cvte.tv.setting")
            .putExtra("toActivity", "com.cvte.tv.setting.TvSettingActivity")
            .putExtra("toAction", Intent.ACTION_MAIN)
            .putExtra("toCategory1", "android.intent.category.TV_HOME")
    }
}
