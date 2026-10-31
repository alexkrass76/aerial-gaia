package app.orchard.tv.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import app.orchard.tv.system.CvteSources

/** Hardware integrations. Product branding and content providers never identify a board. */
internal interface TvPlatformAdapter {
    val id: String
    fun route(context: Context, input: TvInputInfo): Intent?
}

internal object CvtePlatform : TvPlatformAdapter {
    override val id = "cvte"
    // Keep the tested GaiaOS TIF extras and hardware IDs in the existing implementation.
    override fun route(context: Context, input: TvInputInfo) = CvteSources.route(context, input)
}

internal object MediaTekPlatform : TvPlatformAdapter {
    override val id = "mediatek"
    override fun route(context: Context, input: TvInputInfo): Intent? {
        if (input.type != TvInputInfo.TYPE_HDMI || input.serviceInfo.packageName != "com.mediatek.tvinput") return null
        // This override is verified on Cultraview + YaOS. Do not broaden it to untested MTK
        // firmwares: their standard TIF/CEC route may already be correct.
        if (!runCatching { context.packageManager.getPackageInfo("com.yandex.tv.live", 0) }.isSuccess) return null
        return hdmiIntent(input.id).takeIf { LaunchCapability.available(context, it) }
    }
    fun hdmiIntent(physicalInputId: String): Intent = Intent(Intent.ACTION_VIEW,
        TvContract.buildChannelUriForPassthroughInput(physicalInputId))
        .setComponent(ComponentName("com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity"))
}

internal object LaunchCapability {
    fun available(context: Context, intent: Intent): Boolean = runCatching {
        val a = context.packageManager.resolveActivity(intent, 0)?.activityInfo
        a != null && a.exported && a.enabled && a.applicationInfo.enabled
    }.getOrDefault(false)
}

internal object TvPlatforms {
    // Specific tested overrides first; null leaves all upstream vendor and generic fallbacks intact.
    private val adapters: List<TvPlatformAdapter> = listOf(CvtePlatform, MediaTekPlatform)
    fun route(context: Context, input: TvInputInfo): Intent? =
        adapters.firstNotNullOfOrNull { it.route(context, input) }
}
