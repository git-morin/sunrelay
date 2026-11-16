package dev.morin.sunrelay.update

import android.content.Context
import dev.morin.sunrelay.util.objects
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateResult {
    data class Available(val version: String, val url: String, val prerelease: Boolean) : UpdateResult
    data class UpToDate(val current: String) : UpdateResult
}

class UpdateChecker(private val repository: String = REPOSITORY) {

    fun check(currentVersion: String, includePrereleases: Boolean): UpdateResult {
        val current = SemanticVersion.parse(currentVersion) ?: SemanticVersion(0, 0, 0)
        val latest = releases()
            .filter { !it.draft && (includePrereleases || !it.prerelease) }
            .maxByOrNull { it.version }
        return if (latest != null && latest.version > current) {
            UpdateResult.Available(latest.version.toString(), latest.url, latest.prerelease)
        } else {
            UpdateResult.UpToDate(currentVersion)
        }
    }

    private fun releases(): List<Release> {
        val conn = URL("https://api.github.com/repos/$repository/releases?per_page=$PAGE_SIZE").openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "SunRelay")
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> throw IllegalStateException("No releases are published yet")
                HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException("GitHub rate limit reached, try again later")
                in 400..599 -> throw IllegalStateException("GitHub returned HTTP $code")
            }
            return JSONArray(conn.inputStream.bufferedReader().readText()).objects().mapNotNull { release ->
                SemanticVersion.parse(release.optString("tag_name"))?.let { version ->
                    Release(version, release.optString("html_url"), release.optBoolean("prerelease"), release.optBoolean("draft"))
                }
            }
        } catch (e: IOException) {
            throw IllegalStateException("Can't reach GitHub: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private class Release(val version: SemanticVersion, val url: String, val prerelease: Boolean, val draft: Boolean)

    companion object {
        const val REPOSITORY = "git-morin/sunrelay"
        const val REPOSITORY_URL = "https://github.com/$REPOSITORY"
        private const val PAGE_SIZE = 30
        private const val TIMEOUT_MS = 8000

        fun installedVersion(context: Context): String =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }
}
