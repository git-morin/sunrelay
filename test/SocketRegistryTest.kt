package dev.morin.sunrelay.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.IOException

class SocketRegistryTest {
    private class FakeSocket : Closeable {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    @Test
    fun closeAllClosesTrackedSockets() {
        val registry = SocketRegistry()
        val socket = registry.track(FakeSocket())
        registry.closeAll()
        assertTrue(socket.closed)
        assertEquals(0, registry.size)
    }

    @Test
    fun socketsTrackedAfterAStopAreClosedRightAway() {
        val registry = SocketRegistry()
        registry.closeAll()
        val late = FakeSocket()
        assertThrows(RelayClosedException::class.java) { registry.track(late) }
        assertTrue(late.closed)
        assertEquals(0, registry.size)
    }

    @Test
    fun aFailedSetupClosesTheSocket() {
        val registry = SocketRegistry()
        val socket = FakeSocket()
        assertThrows(IOException::class.java) { registry.open(socket) { throw IOException("no route") } }
        assertTrue(socket.closed)
        assertEquals(0, registry.size)
    }

    @Test
    fun aSuccessfulSetupKeepsTheSocketOpen() {
        val registry = SocketRegistry()
        val socket = registry.open(FakeSocket()) {}
        assertFalse(socket.closed)
        assertEquals(1, registry.size)
    }
}
