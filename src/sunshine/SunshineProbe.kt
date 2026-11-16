package dev.morin.sunrelay.sunshine

import java.net.HttpURLConnection
import java.net.URL

object SunshineProbe {
    const val PORT = 47989

    fun fetch(host: String, timeoutMs: Int = 4000): Result<ServerInfo> = runCatching {
        val conn = URL("http://${urlHost(host)}:$PORT/serverinfo").openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        try {
            val body = conn.inputStream.bufferedReader().readText()
            fun tag(name: String) = Regex("<$name>([^<]*)</$name>").find(body)?.groupValues?.get(1)
            ServerInfo(tag("hostname"), tag("state"), tag("appversion"))
        } finally {
            conn.disconnect()
        }
    }
}

internal fun urlHost(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host
