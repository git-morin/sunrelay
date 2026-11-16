package dev.morin.sunrelay.config

interface SettingsReader {
    fun string(key: String): String?

    object Empty : SettingsReader {
        override fun string(key: String): String? = null
    }
}

interface SettingsWriter {
    fun string(key: String, value: String)
}
