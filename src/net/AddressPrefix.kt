package dev.morin.sunrelay.net

import java.net.InetAddress

data class AddressPrefix(val network: InetAddress, val length: Int) {
    fun contains(address: InetAddress): Boolean {
        val a = network.address
        val b = address.address
        if (a.size != b.size || length !in 0..a.size * 8) return false
        val fullBytes = length / 8
        for (i in 0 until fullBytes) if (a[i] != b[i]) return false
        val rest = length % 8
        if (rest == 0) return true
        val mask = (0xff shl (8 - rest)) and 0xff
        return (a[fullBytes].toInt() and mask) == (b[fullBytes].toInt() and mask)
    }
}
