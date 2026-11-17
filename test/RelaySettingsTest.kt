package dev.morin.sunrelay.config

import org.junit.Assert.assertEquals
import org.junit.Test

class RelaySettingsTest {
    @Test
    fun parsesCommaAndSpaceSeparatedPorts() {
        assertEquals(listOf(47984, 47989, 48010), RelaySettings(tcpPorts = "47984, 47989 48010,47984").tcpPortList)
    }

    @Test
    fun reportsWhatItIgnores() {
        assertEquals(listOf("abc", "70000", "0"), RelaySettings.invalidPorts("47984,abc, 70000 0,,"))
        assertEquals(emptyList<String>(), RelaySettings.invalidPorts(RelaySettings().udpPorts))
    }

    @Test
    fun clampsUdpIdleTimeout() {
        assertEquals(5, RelaySettings().withUdpIdle(1).udpIdleSec)
        assertEquals(3600, RelaySettings().withUdpIdle(99999).udpIdleSec)
        assertEquals(30_000L, RelaySettings().withUdpIdle(30).udpIdleMs)
    }
}
