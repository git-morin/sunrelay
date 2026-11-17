package dev.morin.sunrelay.relay

import dev.morin.sunrelay.config.RelaySettings
import dev.morin.sunrelay.net.AddressPrefix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class RelayTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val allowLoopback = ClientFilter({ listOf(AddressPrefix(loopback, 8)) })

    @Test
    fun throughputIsBitsPerSecondOverTheInterval() {
        val meter = ThroughputMeter(2000)
        assertEquals(4000L to 8000L, meter.sample(1000, 2000))
        assertEquals(0L to 4000L, meter.sample(1000, 3000))
    }

    @Test
    fun tcpIsForwardedBothWays() {
        // The relay listens on the same port as the target, so the target sits on another loopback address.
        val target = InetAddress.getByName("127.0.0.2")
        val upstream = ServerSocket(0, 1, target)
        val port = upstream.localPort
        val echo = thread(isDaemon = true) {
            upstream.accept().use { s -> s.getOutputStream().write(s.getInputStream().readNBytes(5).reversedArray()) }
        }
        val relay = Relay(target, RelaySettings(tcpPorts = "$port", udpPorts = ""), allowLoopback, {}, loopback)
        assertEquals(emptyList<String>(), relay.start())
        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(loopback, port), 2000)
                client.soTimeout = 2000
                client.getOutputStream().write("hello".toByteArray())
                assertArrayEquals("olleh".toByteArray(), client.getInputStream().readNBytes(5))
            }
            echo.join(2000)
            assertEquals(5L, relay.stats.bytesUp.get())
            assertEquals(5L, relay.stats.bytesDown.get())
        } finally {
            relay.stop()
            upstream.close()
        }
    }

    @Test
    fun udpIsForwardedBothWays() {
        val target = InetAddress.getByName("127.0.0.2")
        val upstream = DatagramSocket(InetSocketAddress(target, 0)).apply { soTimeout = 2000 }
        val port = upstream.localPort
        val relay = Relay(target, RelaySettings(tcpPorts = "", udpPorts = "$port"), allowLoopback, {}, loopback)
        assertEquals(emptyList<String>(), relay.start())
        try {
            DatagramSocket().use { client ->
                client.soTimeout = 2000
                client.send(DatagramPacket("ping".toByteArray(), 4, InetSocketAddress(loopback, port)))
                val received = DatagramPacket(ByteArray(16), 16)
                upstream.receive(received)
                assertEquals("ping", String(received.data, 0, received.length))
                upstream.send(DatagramPacket("pong".toByteArray(), 4, received.socketAddress))
                val reply = DatagramPacket(ByteArray(16), 16)
                client.receive(reply)
                assertEquals("pong", String(reply.data, 0, reply.length))
            }
        } finally {
            relay.stop()
            upstream.close()
        }
    }

    @Test
    fun clientsOffTheHotspotAreRefused() {
        val target = InetAddress.getByName("127.0.0.2")
        val upstream = ServerSocket(0, 1, target)
        val port = upstream.localPort
        val relay = Relay(target, RelaySettings(tcpPorts = "$port", udpPorts = ""), ClientFilter({ emptyList() }), {}, loopback)
        relay.start()
        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(loopback, port), 2000)
                client.soTimeout = 2000
                assertEquals(-1, runCatching { client.getInputStream().read() }.getOrDefault(-1))
            }
            assertEquals(0L, relay.stats.tcpActive.get())
        } finally {
            relay.stop()
            upstream.close()
        }
    }
}
