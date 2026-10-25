package app.orchard.tv.system

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GaiaSettingsPagesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val action = "android.settings.DATE_SETTINGS"
    private fun register(exported: Boolean = true, enabled: Boolean = true, system: Boolean = true, permission: String? = null) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "example.settings.DatePage"
                packageName = "example.settings"
                this.exported = exported
                this.enabled = enabled
                this.permission = permission
                applicationInfo = ApplicationInfo().apply {
                    this.enabled = true
                    flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), info)
    }
    @Test fun absentPageDoesNotFallBackToGeneralSettings() {
        assertNull(GaiaSettingsPages.resolve(context, action))
    }
    @Test fun exportedSystemPageIsPinned() {
        register()
        val result = GaiaSettingsPages.resolve(context, action)
        assertEquals("example.settings", result?.component?.packageName)
        assertEquals(action, result?.action)
    }
    @Test fun privatePageIsRejected() {
        register(exported = false)
        assertNull(GaiaSettingsPages.resolve(context, action))
    }
    @Test fun disabledPageIsRejected() {
        register(enabled = false)
        assertNull(GaiaSettingsPages.resolve(context, action))
    }
    @Test fun ThirdPartyHandlerIsRejected() {
        register(system = false)
        assertNull(GaiaSettingsPages.resolve(context, action))
    }
    @Test fun privilegedPageIsRejected() {
        register(permission = "android.permission.MASTER_CLEAR")
        assertNull(GaiaSettingsPages.resolve(context, action))
    }
}
