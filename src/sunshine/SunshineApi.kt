package dev.morin.sunrelay.sunshine

import dev.morin.sunrelay.util.objects
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

data class PairedClient(val name: String, val uuid: String, val enabled: Boolean)

data class PendingPairing(val id: String, val name: String, val address: String) {
    val displayName: String get() = name.ifBlank { "A device" }
}

class SunshineApiException(val code: Int, message: String) : Exception(message)

class SunshineApi(
    private val host: String,
    private val user: String,
    private val password: String,
    private val pins: PinStore = SunshinePins,
) {

    fun clients(): List<PairedClient> =
        request("GET", "/api/clients/list").optJSONArray("named_certs").objects().map {
            PairedClient(it.optString("name"), it.optString("uuid"), it.opt("enabled")?.toString() != "false")
        }

    fun unpair(uuid: String) {
        val response = request("POST", "/api/clients/unpair", JSONObject().put("uuid", uuid))
        if (!response.optBoolean("status", false)) throw SunshineApiException(0, "Sunshine refused to unpair")
    }

    fun pending(): List<PendingPairing> {
        val response = try {
            request("GET", "/api/pin")
        } catch (e: SunshineApiException) {
            if (e.code in UNSUPPORTED) return emptyList() else throw e
        }
        return response.optJSONArray("pairings").objects().map {
            PendingPairing(it.optString("id"), it.optString("name"), it.optString("address"))
        }
    }

    fun submitPin(pin: String, name: String, pairingId: String?): Boolean {
        val body = JSONObject().put("pin", pin).put("name", name)
        if (pairingId != null) body.put("pairing_id", pairingId)
        return request("POST", "/api/pin", body).optBoolean("status", false)
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val conn = URL("https://${urlHost(host)}:$PORT$path").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf(CertificatePins.trustManager(host, pins)), SecureRandom()) }
            .socketFactory
        // Sunshine's certificate never names the host; the pin above is what identifies it.
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray()))
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw SunshineApiException(code, "Wrong Sunshine username or password")
            if (code >= 400) {
                val error = runCatching { JSONObject(text).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                throw SunshineApiException(code, error ?: "HTTP $code")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: SSLException) {
            if (e.causes().any { it is PinMismatchException }) throw SunshineApiException(0, CERTIFICATE_CHANGED)
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val PORT = 47990
        val UNSUPPORTED = setOf(404, 405)
        const val CERTIFICATE_CHANGED =
            "Sunshine's certificate changed since SunRelay first connected. If you reinstalled or reset Sunshine, " +
                "tap Forget certificate in Settings → Sunshine login."

        fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause.takeIf { c -> c !== it } }
    }
}
