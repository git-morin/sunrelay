package dev.morin.sunrelay.config

interface SettingsReader {
    fun string(key: String): String?

    /** A value saved with [SettingsWriter.secret], decrypted. */
    fun secret(key: String): String?

    object Empty : SettingsReader {
        override fun string(key: String): String? = null
        override fun secret(key: String): String? = null
    }
}

interface SettingsWriter {
    fun string(key: String, value: String)

    /** Saves a password or API token encrypted. */
    fun secret(key: String, value: String)
}
