package dev.morin.sunrelay.update

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return other.prerelease.size.coerceAtMost(1) - prerelease.size.coerceAtMost(1)
        }
        prerelease.zip(other.prerelease).forEach { (a, b) ->
            val result = compareIdentifiers(a, b)
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (prerelease.isEmpty()) "" else "-" + prerelease.joinToString(".")

    private fun compareIdentifiers(a: String, b: String): Int {
        val numA = a.toIntOrNull()
        val numB = b.toIntOrNull()
        return when {
            numA != null && numB != null -> numA.compareTo(numB)
            numA != null -> -1
            numB != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+.*)?$""")

        fun parse(text: String): SemanticVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return SemanticVersion(major.toInt(), minor.toInt(), patch.toInt(), pre.split('.').filter { it.isNotEmpty() })
        }
    }
}
