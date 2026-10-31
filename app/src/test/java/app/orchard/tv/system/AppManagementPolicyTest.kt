package app.orchard.tv.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagementPolicyTest {
    @Test fun protectsBootInputsConnectivityAndAirplay() {
        listOf("android", "app.orchard.tv.gaia", "com.android.systemui",
            "com.cvte.tv.setting", "com.cvte.tv.api.impl", "com.cvte.tv.launcher.mars",
            "com.droidlogic.tvinput", "com.wireguard.android", "io.github.jqssun.airplay",
            "com.maxhub.screenshare.server.tv", "com.screenshare.platform",
            "com.mediatek.tvinput", "com.mediatek.wwtv.tvcenter", "com.ctv.android.tvboot",
            "com.yandex.tv.services.platform", "com.yandex.tv.services", "com.yandex.webview",
            "ru.yandex.androidkeyboard.tv", "com.yandex.launcher.updaterapp",
            "com.yandex.tv.live", "com.yandex.tv.input.efir").forEach {
            assertFalse("Must protect $it", AppManagementPolicy.allows(it))
        }
    }
    @Test fun permitsOrdinaryUserApps() {
        listOf("org.localsend.localsend_app", "com.vk.tv", "ru.kinopoisk.tv", "ru.kinopoisk.yandex.tv", "com.yandex.tv.music").forEach {
            assertTrue(AppManagementPolicy.allows(it))
        }
    }
}
