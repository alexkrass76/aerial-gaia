package app.orchard.tv.platform

import android.content.Context
import android.content.Intent
import app.orchard.tv.R
import app.orchard.tv.channels.ChannelsActivity
import app.orchard.tv.system.SourceKind
import app.orchard.tv.system.TvSource

/** Optional content integration, independent of CVTE/MediaTek/other hardware adapters. */
internal object YaosContent {
    fun source(context: Context): TvSource? {
        val player = Intent("yandex.intent.action.TV_CHANNEL_RUBRICATOR").setPackage("com.yandex.tv.live")
        if (!LaunchCapability.available(context, player)) return null
        return TvSource(
            // Persisted in pin/order/customization preferences: never rename this ID.
            id = "yaos:channels", label = context.getString(R.string.yaos_channels),
            kind = SourceKind.Tuner, connected = false, deviceName = null,
            intent = Intent(context, ChannelsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
