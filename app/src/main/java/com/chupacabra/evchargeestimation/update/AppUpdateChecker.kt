package com.chupacabra.evchargeestimation.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.chupacabra.evchargeestimation.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer APK and downloads it for install.
 * Uses only the public Releases API (no token). Requires network for the check.
 */
class AppUpdateChecker(
    private val context: Context,
    private val gson: Gson = Gson()
) {

    data class AvailableUpdate(
        val versionName: String,
        val tagName: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long,
        val htmlUrl: String
    )

    sealed class CheckResult {
        data class UpdateAvailable(val update: AvailableUpdate) : CheckResult()
        data object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(BuildConfig.UPDATE_CHECK_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EVChargeEstimation/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code == 404) {
                return@withContext CheckResult.UpToDate
            }
            if (code !in 200..299) {
                return@withContext CheckResult.Error("Could not check for updates ($code)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = gson.fromJson(body, GithubRelease::class.java)
                ?: return@withContext CheckResult.Error("Unexpected response from GitHub")

            val remoteName = release.tagName.removePrefix("v").trim()
            if (!isNewerVersion(remoteName, BuildConfig.VERSION_NAME)) {
                return@withContext CheckResult.UpToDate
            }

            val apk = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    !it.name.contains("debug", ignoreCase = true)
            } ?: return@withContext CheckResult.Error("Update found but no APK is attached")

            CheckResult.UpdateAvailable(
                AvailableUpdate(
                    versionName = remoteName,
                    tagName = release.tagName,
                    releaseNotes = release.body.orEmpty().take(800),
                    apkDownloadUrl = apk.browserDownloadUrl,
                    apkSizeBytes = apk.size,
                    htmlUrl = release.htmlUrl
                )
            )
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Update check failed")
        }
    }

    /**
     * Download APK to app cache. [onProgress] is 0..100.
     */
    suspend fun downloadApk(
        update: AvailableUpdate,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(dir, "EVChargeEstimation-${update.versionName}.apk")
        if (outFile.exists()) outFile.delete()

        val conn = (URL(update.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "EVChargeEstimation/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/octet-stream")
        }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Download failed (${conn.responseCode})")
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.apkSizeBytes
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                    readTotal += n
                    if (total > 0) {
                        val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
                output.flush()
            }
        }
        onProgress(100)
        outFile
    }

    fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canRequestInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openReleasePage(update: AvailableUpdate) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        /**
         * True if [remote] is a higher semantic version than [local]
         * (e.g. 1.0.1 > 1.0.0). Non-numeric suffixes are ignored after the numeric parts.
         */
        fun isNewerVersion(remote: String, local: String): Boolean {
            val r = parseVersion(remote)
            val l = parseVersion(local)
            val len = maxOf(r.size, l.size)
            for (i in 0 until len) {
                val rv = r.getOrElse(i) { 0 }
                val lv = l.getOrElse(i) { 0 }
                if (rv != lv) return rv > lv
            }
            return false
        }

        private fun parseVersion(raw: String): List<Int> {
            val cleaned = raw.trim().removePrefix("v")
            val numeric = cleaned.takeWhile { it.isDigit() || it == '.' }
            if (numeric.isBlank()) return listOf(0)
            return numeric.split('.').map { it.toIntOrNull() ?: 0 }
        }
    }

    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String = "",
        @SerializedName("html_url") val htmlUrl: String = "",
        val body: String? = null,
        val assets: List<GithubAsset> = emptyList()
    )

    private data class GithubAsset(
        val name: String = "",
        val size: Long = 0,
        @SerializedName("browser_download_url") val browserDownloadUrl: String = ""
    )
}
