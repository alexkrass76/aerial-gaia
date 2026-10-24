package app.orchard.tv.system

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Phone settings need a physical LAN address, not the default Internet/VPN address. */
internal object LanAddress {
    data class Candidate(val capabilities: NetworkCapabilities, val links: LinkProperties, val active: Boolean)

    fun find(cm: ConnectivityManager): String? {
        val active = cm.currentNetwork()
        return select(cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            val links = cm.getLinkProperties(network) ?: return@mapNotNull null
            Candidate(caps, links, network == active)
        })
    }

    fun select(networks: List<Candidate>): String? = networks
        .filter {
            // VPN capabilities can include WIFI or ETHERNET as their underlying transport.
            !it.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (it.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        // Prefer the actual default LAN; behind VPN use wired before wireless, consistently.
        // Internet validation is deliberately not required for local phone access.
        .sortedWith(compareByDescending<Candidate> { it.active }
            .thenByDescending { it.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) })
        .firstNotNullOfOrNull { candidate ->
            candidate.links.linkAddresses.map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress &&
                    !it.isAnyLocalAddress && !it.isMulticastAddress }?.hostAddress
        }
}
