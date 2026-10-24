package gaia.bridge;

/** Services required by this port must not be closed from the task UI. */
public final class TaskClosePolicy {
    public static boolean allows(String pkg) {
        return pkg != null && !pkg.equals("android") &&
            !pkg.startsWith("app.orchard.tv") && !pkg.startsWith("com.android.") &&
            !pkg.startsWith("com.cvte.") && !pkg.startsWith("com.droidlogic.") &&
            !pkg.equals("com.wireguard.android") && !pkg.equals("io.github.jqssun.airplay") &&
            !pkg.equals("com.maxhub.screenshare.server.tv") && !pkg.equals("com.screenshare.platform");
    }
}
