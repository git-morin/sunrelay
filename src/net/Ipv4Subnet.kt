package dev.morin.sunrelay.net

import java.net.Inet4Address

data class Ipv4Subnet(val base: Int, val prefix: Int) {
    fun hostAddresses(): Sequence<String> {
        val size = 1 shl (32 - prefix)
        return (1 until size - 1).asSequence().map { format(base + it) }
    }

    companion object {
        private const val WIDEST_PREFIX = 22

        fun scannable(address: Int, prefix: Int, ownAddresses: List<Int>): Ipv4Subnet? {
            if (prefix >= 32) return null
            if (prefix >= WIDEST_PREFIX) return Ipv4Subnet(address and mask(prefix), prefix)
            val wide = mask(prefix)
            val own = ownAddresses.firstOrNull { (it and wide) == (address and wide) } ?: return null
            return Ipv4Subnet(own and mask(WIDEST_PREFIX), WIDEST_PREFIX)
        }

        fun toInt(address: Inet4Address): Int =
            address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }

        private fun mask(prefix: Int) = if (prefix == 0) 0 else -1 shl (32 - prefix)

        private fun format(v: Int) = "${(v ushr 24) and 0xff}.${(v ushr 16) and 0xff}.${(v ushr 8) and 0xff}.${v and 0xff}"
    }
}
