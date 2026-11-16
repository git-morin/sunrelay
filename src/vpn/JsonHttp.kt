package dev.morin.sunrelay.vpn

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JsonHttp(private val service: String) {

    fun get(url: String, authorization: String): HttpResult = execute(url) {
        setRequestProperty("Authorization", authorization)
    }

    fun postForm(url: String, form: Map<String, String>): HttpResult = execute(url) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val encoded = form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        outputStream.use { it.write(encoded.toByteArray()) }
    }

    fun httpsBase(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "Enter your $service server URL" }
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        require(withScheme.startsWith("https://", ignoreCase = true)) { "The $service server URL must use https" }
        return withScheme
    }

    private fun execute(url: String, configure: HttpURLConnection.() -> Unit): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        try {
            conn.configure()
            val code = conn.responseCode
            val body = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            return HttpResult(service, code, body)
        } catch (e: IOException) {
            throw IllegalStateException("Can't reach the $service API: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8000
        const val READ_TIMEOUT_MS = 15000
    }
}

class HttpResult(private val service: String, val code: Int, val body: String) {
    val isNotFound: Boolean get() = code == 404

    fun bodyOrThrow(messages: Map<Int, String> = emptyMap()): String {
        if (code < 400) return body
        throw IllegalStateException(
            messages[code] ?: when (code) {
                401, 403 -> "$service rejected the API token"
                else -> "$service API: HTTP $code"
            },
        )
    }
}
