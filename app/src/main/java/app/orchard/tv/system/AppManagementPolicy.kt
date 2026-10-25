package app.orchard.tv.system

/** Protect the port, connectivity and native TV services independently of UI entry points. */
internal object AppManagementPolicy {
    fun allows(pkg: String): Boolean = pkg != "android" &&
        !pkg.startsWith("app.orchard.tv") && !pkg.startsWith("com.android.") &&
        !pkg.startsWith("com.cvte.") && !pkg.startsWith("com.droidlogic.") &&
        pkg !in setOf("com.wireguard.android", "io.github.jqssun.airplay",
            "com.maxhub.screenshare.server.tv", "com.screenshare.platform")
}
