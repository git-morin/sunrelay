package dev.morin.sunrelay.sunshine

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.X509TrustManager

interface PinStore {
    fun get(host: String): String?
    fun put(host: String, pin: String)
    fun remove(host: String)
}

class PinMismatchException(val host: String) : CertificateException("Sunshine's certificate for $host changed")

/** Sunshine uses a self-signed certificate, so trust the first one seen for each host and refuse any other after. */
object CertificatePins {
    fun check(host: String, pin: String, store: PinStore) {
        val key = key(host)
        when (store.get(key)) {
            null -> store.put(key, pin)
            pin -> Unit
            else -> throw PinMismatchException(host)
        }
    }

    fun fingerprint(certificate: X509Certificate): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))

    fun trustManager(host: String, store: PinStore): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("Client certificates aren't supported")

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("Sunshine sent no certificate")
            check(host, fingerprint(leaf), store)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun key(host: String) = host.trim().lowercase()
}

/** Pins saved on this phone (excluded from backups with the rest of the app's preferences). */
object SunshinePins : PinStore {
    @Volatile
    private var prefs: SharedPreferences? = null
    private val memory = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun has(host: String) = get(CertificatePins.key(host)) != null

    fun forget(host: String) = remove(CertificatePins.key(host))

    override fun get(host: String): String? = prefs?.getString(host, null) ?: memory[host]

    override fun put(host: String, pin: String) {
        prefs?.edit()?.putString(host, pin)?.apply() ?: memory.put(host, pin)
    }

    override fun remove(host: String) {
        prefs?.edit()?.remove(host)?.apply()
        memory.remove(host)
    }

    private const val PREFS = "sunshine_pins"
}
