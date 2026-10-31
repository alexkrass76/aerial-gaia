package app.orchard.tv.platform

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import app.orchard.tv.BuildConfig
import app.orchard.tv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlatformCompatibilityTest {
    @Test fun rebrandingPreservesInstalledIdentity() {
        assertEquals("app.orchard.tv.gaia", BuildConfig.APPLICATION_ID)
        assertEquals("Aerial UI", RuntimeEnvironment.getApplication().getString(R.string.app_name))
    }
    @Test fun mediaTekKeepsPhysicalPassthroughAndStockPlayer() {
        val id = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
        val route = MediaTekPlatform.hdmiIntent(id)
        assertEquals(Intent.ACTION_VIEW, route.action)
        assertEquals("com.mediatek.wwtv.tvcenter", route.component?.packageName)
        assertEquals(Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(id)), route.data)
    }
    @Test fun yaosContentDoesNotRequireSpecificHardwareAndRejectsDisabledPlayer() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent("yandex.intent.action.TV_CHANNEL_RUBRICATOR").setPackage("com.yandex.tv.live")
        val activity = ActivityInfo().apply {
            packageName = "com.yandex.tv.live"; name = "com.yandex.tv.live.playback.ChannelPlaybackActivity"
            exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = "com.yandex.tv.live"; enabled = true }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply { activityInfo = activity })
        assertEquals("yaos:channels", YaosContent.source(app)?.id)
        activity.applicationInfo.enabled = false
        assertNull(YaosContent.source(app))
    }
}
