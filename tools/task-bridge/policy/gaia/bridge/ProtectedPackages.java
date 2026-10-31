package gaia.bridge;

/** Shared app-management and task policy. No device mutation or package re-enabling. */
public final class ProtectedPackages {
    private ProtectedPackages() {}
    public static boolean contains(String pkg) {
        if (pkg == null || pkg.equals("android")) return true;
        for (String prefix : new String[]{"app.orchard.tv", "com.android.", "com.cvte.",
                "com.droidlogic.", "com.mediatek.", "com.ctv."})
            if (pkg.startsWith(prefix)) return true;
        // Verified YaOS dependencies; do not blanket-block user applications from Yandex.
        for (String name : new String[]{"mediatek.factorymenu.ui", "com.yandex.tv.home",
                "com.yandex.tv.services", "com.yandex.tv.services.platform",
                "com.yandex.tv.services.privileged", "com.yandex.tv.vendor.config",
                "com.yandex.server.SystemExtensionService", "com.yandex.webview",
                "ru.yandex.androidkeyboard.tv", "com.yandex.tv.input.efir", "com.yandex.tv.live",
                "com.yandex.devices.player", "com.yandex.tv.videoplayer", "com.yandex.tv.webplayer",
                "com.yandex.tv.settings", "com.yandex.tv.setupwizard", "com.yandex.launcher.updaterapp",
                "com.yandex.io.sdk", "com.wireguard.android", "io.github.jqssun.airplay",
                "com.maxhub.screenshare.server.tv", "com.screenshare.platform"})
            if (pkg.equals(name)) return true;
        return false;
    }
}
