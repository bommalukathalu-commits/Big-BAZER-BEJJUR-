package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionTag: String,
    val buildNumber: Int,
    val downloadUrl: String,
    val releasePageUrl: String,
    val releaseNotes: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/bommalukathalu-commits/Big-BAZER-BEJJUR-/releases/latest"
    private const val ALL_RELEASES_API =
        "https://api.github.com/repos/bommalukathalu-commits/Big-BAZER-BEJJUR-/releases?per_page=1"
    private const val FALLBACK_RELEASE_URL =
        "https://github.com/bommalukathalu-commits/Big-BAZER-BEJJUR-/releases/latest"
    private const val AUTO_CHECK_COOLDOWN_MS = 10L * 60L * 1000L

    @Volatile
    private var lastCheckedAtMs: Long = 0L

    fun currentVersionLabel(): String = "v${BuildConfig.VERSION_NAME}"

    fun shouldAutoCheckNow(): Boolean {
        val now = System.currentTimeMillis()
        return lastCheckedAtMs == 0L || (now - lastCheckedAtMs) >= AUTO_CHECK_COOLDOWN_MS
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        lastCheckedAtMs = System.currentTimeMillis()
        runCatching {
            val primaryJson = fetchJson(LATEST_RELEASE_API)
            val releaseObj = primaryJson ?: fetchFirstReleaseFromList()
                ?: return@withContext UpdateCheckResult.Error("Unable to reach update server")

            val tag = releaseObj.optString("tag_name", "").trim()
            val htmlUrl = releaseObj.optString("html_url", FALLBACK_RELEASE_URL).ifBlank { FALLBACK_RELEASE_URL }
            val notes = releaseObj.optString("body", "").trim()

            var apkUrl = htmlUrl
            val assets = releaseObj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val url = asset.optString("browser_download_url", "")
                    if (url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        break
                    }
                }
            }

            val remoteBuild = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val currentBuild = BuildConfig.VERSION_CODE

            if (remoteBuild > currentBuild) {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        versionTag = tag.ifBlank { "v1.0.$remoteBuild" },
                        buildNumber = remoteBuild,
                        downloadUrl = apkUrl,
                        releasePageUrl = htmlUrl,
                        releaseNotes = notes
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error(err.localizedMessage ?: "Network unavailable")
        }
    }

    private fun fetchJson(apiUrl: String): JSONObject? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BigBazerBejjurAndroid/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFirstReleaseFromList(): JSONObject? {
        val conn = (URL(ALL_RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BigBazerBejjurAndroid/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                if (arr.length() > 0) arr.optJSONObject(0) else null
            }
        } finally {
            conn.disconnect()
        }
    }

    fun openUpdateDownload(context: Context, info: AppUpdateInfo) {
        val primary = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
        runCatching { context.startActivity(primary) }
            .onFailure { runCatching { context.startActivity(fallback) } }
    }
}
