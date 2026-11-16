package dev.morin.sunrelay.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    private fun v(text: String) = requireNotNull(SemanticVersion.parse(text))

    @Test
    fun parsesTagsWithAndWithoutPrefix() {
        assertEquals(SemanticVersion(1, 2, 3), v("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3, listOf("beta", "4")), v("1.2.3-beta.4"))
        assertNull(SemanticVersion.parse("latest"))
    }

    @Test
    fun releaseOutranksItsPrereleases() {
        assertTrue(v("1.2.0") > v("1.2.0-beta.9"))
        assertTrue(v("1.2.0-beta.1") > v("1.1.9"))
    }

    @Test
    fun prereleaseIdentifiersCompareNumerically() {
        assertTrue(v("1.0.0-beta.10") > v("1.0.0-beta.9"))
        assertTrue(v("1.0.0-beta.1") > v("1.0.0-beta"))
        assertTrue(v("1.0.0-beta") > v("1.0.0-alpha"))
    }

    @Test
    fun orderingPicksLatest() {
        val latest = listOf("1.0.0", "1.1.0-beta.2", "1.0.1", "1.1.0-beta.10").map(::v).max()
        assertEquals(v("1.1.0-beta.10"), latest)
    }
}
