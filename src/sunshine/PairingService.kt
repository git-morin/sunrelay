package dev.morin.sunrelay.sunshine

data class PairingResult(val accepted: Boolean, val newClientUuid: String?)

class PairingService(private val api: SunshineApi) {

    fun pair(request: PendingPairing, pin: String): PairingResult {
        val before = clientIds()
        val accepted = api.submitPin(pin, request.name.ifBlank { DEFAULT_NAME }, request.id.ifBlank { null })
        if (!accepted) return PairingResult(false, null)
        repeat(POLL_ATTEMPTS) {
            Thread.sleep(POLL_INTERVAL_MS)
            val added = clientIds() - before
            if (added.isNotEmpty()) return PairingResult(true, added.first())
        }
        return PairingResult(true, null)
    }

    private fun clientIds(): Set<String> =
        runCatching { api.clients().map { it.uuid }.toSet() }.getOrDefault(emptySet())

    private companion object {
        const val DEFAULT_NAME = "Moonlight (SunRelay)"
        const val POLL_ATTEMPTS = 10
        const val POLL_INTERVAL_MS = 500L
    }
}
