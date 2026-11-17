package dev.morin.sunrelay.vpn

import dev.morin.sunrelay.sunshine.ServerInfo
import dev.morin.sunrelay.sunshine.SunshineProbe
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Probes in parallel, giving up on whatever hasn't answered once [budgetSeconds] have passed for the whole scan. */
internal class HostProber(private val threads: Int, private val timeoutMs: Int, private val budgetSeconds: Long) {

    fun <T> probe(
        items: List<T>,
        address: (T) -> String,
        onProbed: (done: Int, total: Int) -> Unit,
        toHost: (T, ServerInfo) -> FoundHost,
    ): List<FoundHost> {
        val done = AtomicInteger()
        onProbed(0, items.size)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = items.map { item ->
                pool.submit(Callable {
                    SunshineProbe.fetch(address(item), timeoutMs).getOrNull()?.let { toHost(item, it) }
                        .also { onProbed(done.incrementAndGet(), items.size) }
                })
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(budgetSeconds)
            return futures.mapNotNull { future ->
                try {
                    future.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
                } catch (e: InterruptedException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        val forDevices = HostProber(threads = 16, timeoutMs = 4000, budgetSeconds = 30)
        val forSubnets = HostProber(threads = 64, timeoutMs = 1500, budgetSeconds = 180)
    }
}
