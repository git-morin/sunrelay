package dev.morin.sunrelay.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretCodecTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val codec = SecretCodec { key }

    @Test
    fun roundTripsASecret() {
        val stored = codec.encode("hunter2")
        assertFalse("hunter2" in stored)
        assertTrue(codec.isEncoded(stored))
        assertEquals("hunter2", codec.decode(stored))
    }

    @Test
    fun usesAFreshIvEachTime() {
        assertNotEquals(codec.encode("same"), codec.encode("same"))
    }

    @Test
    fun keepsEmptyValuesEmpty() {
        assertEquals("", codec.encode(""))
        assertEquals("", codec.decode(""))
    }

    @Test
    fun readsPlainValuesFromOlderBuilds() {
        assertFalse(codec.isEncoded("plain-token"))
        assertEquals("plain-token", codec.decode("plain-token"))
    }

    @Test
    fun aDifferentKeyCannotDecrypt() {
        val stored = codec.encode("hunter2")
        assertNull(SecretCodec { newKey() }.decode(stored))
    }

    @Test
    fun aTamperedValueCannotBeDecrypted() {
        val stored = codec.encode("hunter2")
        val flipped = stored.dropLast(2) + if (stored[stored.length - 2] == 'A') "B=" else "A="
        assertNull(codec.decode(flipped))
        assertNull(codec.decode("enc1:AAAA"))
        assertNull(codec.decode("enc1:not base64!"))
    }
}
