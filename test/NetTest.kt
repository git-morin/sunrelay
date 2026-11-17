package dev.morin.sunrelay.net

import dev.morin.sunrelay.sunshine.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class NetTest {
    private fun int(ip: String) = Ipv4Subnet.toInt(InetAddress.getByName(ip) as Inet4Address)

    @Test
    fun narrowSubnetIsScannedWhole() {
        val subnet = Ipv4Subnet.scannable(int("10.147.17.5"), 24, emptyList())!!
        val hosts = subnet.hostAddresses().toList()
        assertEquals(254, hosts.size)
        assertEquals("10.147.17.1", hosts.first())
        assertEquals("10.147.17.254", hosts.last())
    }

    @Test
    fun wideSubnetShrinksAroundOwnAddress() {
        val own = int("100.101.102.103")
        val subnet = Ipv4Subnet.scannable(int("100.64.0.0"), 10, listOf(own))!!
        assertEquals(Ipv4Subnet(int("100.101.100.0"), 22), subnet)
        assertNull(Ipv4Subnet.scannable(int("100.64.0.0"), 10, listOf(int("192.168.1.2"))))
        assertNull(Ipv4Subnet.scannable(own, 32, listOf(own)))
    }

    @Test
    fun tetherKindFromInterfaceName() {
        assertEquals(TetherKind.WiFi, TetherKind.of("swlan0"))
        assertEquals(TetherKind.Usb, TetherKind.of("rndis0"))
        assertEquals(TetherKind.Bluetooth, TetherKind.of("bt-pan"))
        assertNull(TetherKind.of("rmnet_data0"))
        assertNull(TetherKind.of("tun0"))
    }

    @Test
    fun serverStateLabels() {
        assertEquals("ready", ServerInfo("pc", "SUNSHINE_SERVER_FREE", null).stateLabel)
        assertEquals("game running", ServerInfo("pc", "SUNSHINE_SERVER_BUSY", null).stateLabel)
        assertEquals("? · ?", ServerInfo(null, null, null).summary)
    }
}
