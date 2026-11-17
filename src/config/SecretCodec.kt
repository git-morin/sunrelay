package dev.morin.sunrelay.config

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts saved secrets (passwords, API tokens) with AES-GCM. On the phone the key lives in the Android Keystore
 * ([KeystoreKey]) and never leaves it, so the saved values are useless without this app on this device.
 */
class SecretCodec(private val key: () -> SecretKey) {

    fun encode(plain: String): String {
        if (plain.isEmpty()) return ""
        // The Keystore picks the IV itself and refuses one supplied by the caller.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    /**
     * The secret [stored] holds, or null if it can't be decrypted (the key is gone or the value is corrupt).
     * Values saved by builds that didn't encrypt come back unchanged.
     */
    fun decode(stored: String): String? {
        if (!isEncoded(stored)) return stored
        if (stored.isEmpty()) return ""
        return runCatching {
            val sealed = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            require(sealed.size > IV_BYTES) { "too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
        }.getOrNull()
    }

    /** Whether [stored] is already encrypted (or empty), as opposed to a plain value from an older build. */
    fun isEncoded(stored: String): Boolean = stored.isEmpty() || stored.startsWith(PREFIX)

    private companion object {
        const val PREFIX = "enc1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
