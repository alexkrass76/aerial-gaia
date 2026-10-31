package gaia.bridge;

/** Services required by this port must not be closed from the task UI. */
public final class TaskClosePolicy {
    public static boolean allows(String pkg) {
        return !ProtectedPackages.contains(pkg);
    }
}
