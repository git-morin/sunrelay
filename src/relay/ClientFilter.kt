package dev.morin.sunrelay.relay

import dev.morin.sunrelay.net.AddressPrefix
import java.net.InetAddress

/** Accepts only clients on the phone's hotspot or tethering subnets, refreshing them at most every [refreshMs]. */
class ClientFilter(
    private val prefixes: () -> List<AddressPrefix>,
    private val refreshMs: Long = 2000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var cached: List<AddressPrefix> = emptyList()

    @Volatile
    private var cachedAt = Long.MIN_VALUE

    fun allows(address: InetAddress): Boolean {
        val now = clock()
        if (cachedAt == Long.MIN_VALUE || now - cachedAt >= refreshMs) {
            cached = runCatching(prefixes).getOrDefault(emptyList())
            cachedAt = now
        }
        return cached.any { it.contains(address) }
    }
}
