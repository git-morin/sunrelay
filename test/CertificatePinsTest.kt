package dev.morin.sunrelay.sunshine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CertificatePinsTest {
    private class MemoryPins : PinStore {
        val pins = mutableMapOf<String, String>()
        override fun get(host: String) = pins[host]
        override fun put(host: String, pin: String) { pins[host] = pin }
        override fun remove(host: String) { pins.remove(host) }
    }

    @Test
    fun firstCertificateIsTrustedAndRemembered() {
        val store = MemoryPins()
        CertificatePins.check("Gaming-PC", "pin-a", store)
        assertEquals("pin-a", store.pins["gaming-pc"])
        CertificatePins.check(" gaming-pc ", "pin-a", store)
    }

    @Test
    fun changedCertificateIsRefusedUntilForgotten() {
        val store = MemoryPins()
        CertificatePins.check("gaming-pc", "pin-a", store)
        assertThrows(PinMismatchException::class.java) { CertificatePins.check("gaming-pc", "pin-b", store) }
        store.remove(CertificatePins.key("gaming-pc"))
        CertificatePins.check("gaming-pc", "pin-b", store)
        assertEquals("pin-b", store.pins["gaming-pc"])
    }

    @Test
    fun pinsAreKeptPerHost() {
        val store = MemoryPins()
        CertificatePins.check("100.64.0.2", "pin-a", store)
        CertificatePins.check("100.64.0.3", "pin-b", store)
        assertEquals(2, store.pins.size)
    }
}
