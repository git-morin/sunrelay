package dev.morin.sunrelay.relay

import dev.morin.sunrelay.config.RelaySettings
import dev.morin.sunrelay.net.AddressPrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ClientFilterTest {
    private fun ip(text: String) = InetAddress.getByName(text)
    private fun prefix(text: String, length: Int) = AddressPrefix(ip(text), length)

    @Test
    fun prefixMatchesOnlyItsSubnet() {
        val hotspot = prefix("192.168.43.1", 24)
        assertTrue(hotspot.contains(ip("192.168.43.77")))
        assertFalse(hotspot.contains(ip("192.168.44.77")))
        assertFalse(hotspot.contains(ip("100.64.0.5")))
    }

    @Test
    fun prefixHandlesPartialBytesAndFamilies() {
        val p = prefix("10.0.8.1", 21)
        assertTrue(p.contains(ip("10.0.15.255")))
        assertFalse(p.contains(ip("10.0.16.0")))
        assertFalse(p.contains(ip("fd00::1")))
        assertTrue(prefix("fd12:3456::1", 64).contains(ip("fd12:3456::abcd")))
    }

    @Test
    fun filterRefusesEverythingWithoutHotspot() {
        assertFalse(ClientFilter({ emptyList() }).allows(ip("192.168.43.2")))
    }

    @Test
    fun filterRefreshesPrefixesAfterInterval() {
        var now = 0L
        var prefixes = emptyList<AddressPrefix>()
        val filter = ClientFilter({ prefixes }, refreshMs = 1000, clock = { now })
        assertFalse(filter.allows(ip("192.168.43.2")))
        prefixes = listOf(prefix("192.168.43.1", 24))
        now = 500
        assertFalse(filter.allows(ip("192.168.43.2")))
        now = 1000
        assertTrue(filter.allows(ip("192.168.43.2")))
    }

    @Test
    fun legacyDefaultPortsDropTheAdminUi() {
        assertEquals(RelaySettings().tcpPorts, RelaySettings.migrateTcpPorts("47984,47989,47990,48010"))
        assertEquals("47984,47990", RelaySettings.migrateTcpPorts("47984,47990"))
        assertFalse(47990 in RelaySettings().tcpPortList)
    }
}
