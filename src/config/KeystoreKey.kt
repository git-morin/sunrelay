package dev.morin.sunrelay.config

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The app's secret-encryption key, created in the Android Keystore on first use. */
object KeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "sunrelay-secrets"

    @Volatile
    private var cached: SecretKey? = null

    // Synchronized so two first uses can't each generate a key and leave values encrypted with the overwritten one.
    @Synchronized
    fun get(): SecretKey = cached ?: load().also { cached = it }

    private fun load(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
