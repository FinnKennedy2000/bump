package com.finn.bump.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published build: the tag, the `bump.apk` asset's URL and the release notes. */
data class Release(val tag: String, val apkUrl: String, val notes: String)

@Serializable private class GhAsset(val name: String, val browser_download_url: String)

@Serializable
private class GhRelease(val tag_name: String, val body: String? = null, val assets: List<GhAsset> = emptyList())

private const val LATEST = "https://api.github.com/repos/FinnKennedy2000/bump/releases/latest"

/**
 * Self-update from the public GitHub repo's releases (convention: tag `build-<versionCode>`,
 * one asset `bump.apk`). Runs only when the user taps "Check for updates"; every failure is
 * thrown as an [IOException] with a message fit to show on screen.
 */
class AppUpdater(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val body = get(LATEST, "application/vnd.github+json") { code ->
            when (code) {
                404 -> "No release published yet"
                403, 429 -> "GitHub is rate limiting this phone; try again later"
                else -> null
            }
        }.use { it.readBytes().decodeToString() }
        val r = try {
            json.decodeFromString(GhRelease.serializer(), body)
        } catch (e: Exception) {
            throw IOException("Unreadable release from GitHub", e)
        }
        val apk = r.assets.firstOrNull { it.name == "bump.apk" }
            ?: throw IOException("Release ${r.tag_name} has no bump.apk")
        Release(r.tag_name, apk.browser_download_url, r.body.orEmpty().trim())
    }

    /** Downloads to `cacheDir/updates/bump.apk`; a partial file is deleted on any failure. */
    suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "bump.apk")
        out.delete()
        try {
            val conn = open(release.apkUrl, "application/octet-stream")
            try {
                check(conn) { "Download failed (HTTP $it)" }
                val expected = conn.contentLengthLong
                val written = conn.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                if (expected >= 0 && written != expected) {
                    throw IOException("Download was cut short ($written of $expected bytes)")
                }
            } finally {
                conn.disconnect()
            }
            out
        } catch (e: Exception) {
            out.delete()
            throw if (e is IOException) e else IOException("Download failed: ${e.message}", e)
        }
    }

    /**
     * Hands the APK to the system installer. If this app may not install yet, opens the
     * "install unknown apps" setting instead and returns false: the user flips it, then taps Install again.
     */
    fun install(apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String, accept: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "bump-android")
    }

    private fun check(conn: HttpURLConnection, message: (Int) -> String) {
        val code = try {
            conn.responseCode
        } catch (e: IOException) {
            throw IOException("Could not reach GitHub: ${e.message}", e)
        }
        if (code != 200) throw IOException(message(code))
    }

    private fun get(url: String, accept: String, explain: (Int) -> String?): java.io.InputStream {
        val conn = open(url, accept)
        check(conn) { explain(it) ?: "GitHub answered HTTP $it" }
        return conn.inputStream
    }
}
