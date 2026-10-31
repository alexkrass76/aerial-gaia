package app.orchard.tv.system

import gaia.bridge.ProtectedPackages

/** Share dependency protection with the privileged task helper. */
internal object AppManagementPolicy {
    fun allows(pkg: String): Boolean = !ProtectedPackages.contains(pkg)
}
