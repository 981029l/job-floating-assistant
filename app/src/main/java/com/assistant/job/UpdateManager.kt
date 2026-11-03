package com.assistant.job

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val assetSize: Long = 0L
)

object UpdateManager {
    // 通道 1: 全球 CDN 静态 version.json (首选, 永久零频控, 移动端延迟 < 50ms)
    private const val CDN_VERSION_JSON_URL =
        "https://cdn.jsdelivr.net/gh/981029l/job-floating-assistant@main/version.json"
    private const val RAW_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/981029l/job-floating-assistant/main/version.json"

    // 通道 2: Web 网页 302 重定向探针 (走 Web 服务器, 完全不受 60次/小时 REST API 限制)
    private const val GITHUB_WEB_LATEST_RELEASE =
        "https://github.com/981029l/job-floating-assistant/releases/latest"

    // 通道 3: 官方 REST API (作为最后兜底)
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/981029l/job-floating-assistant/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 禁用自动重定向的探针客户端 (用于读取 302 Location 头)
    private val noRedirectHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName(context)

        // 1. 优先尝试通道 1: jsDelivr 全球加速 CDN (彻底免疫 403，时间戳穿透缓存)
        val cdnResult = checkViaVersionJson(CDN_VERSION_JSON_URL, currentVersion)
        if (cdnResult.isSuccess && cdnResult.getOrNull()?.hasUpdate == true) {
            return@withContext cdnResult
        }

        // 备用通道 1b: Raw GitHub version.json
        val rawResult = checkViaVersionJson(RAW_VERSION_JSON_URL, currentVersion)
        if (rawResult.isSuccess && rawResult.getOrNull()?.hasUpdate == true) {
            return@withContext rawResult
        }

        // 2. 尝试通道 2: 网页 302 重定向探针 (读取 HTTP 302 Location Header，官方服务器零缓存零频控)
        val probeResult = checkViaWebRedirect(GITHUB_WEB_LATEST_RELEASE, currentVersion)
        if (probeResult.isSuccess && probeResult.getOrNull()?.hasUpdate == true) {
            val probeInfo = probeResult.getOrNull()!!
            val bestNotes = cdnResult.getOrNull()?.releaseNotes
                ?: rawResult.getOrNull()?.releaseNotes
                ?: probeInfo.releaseNotes
            return@withContext Result.success(probeInfo.copy(releaseNotes = bestNotes))
        }

        // 3. 尝试通道 3: 官方 REST API (最后防线)
        val apiResult = checkViaGithubApi(GITHUB_LATEST_RELEASE_API, currentVersion)
        if (apiResult.isSuccess && apiResult.getOrNull()?.hasUpdate == true) {
            return@withContext apiResult
        }

        // 4. 若所有连通通道均判定无需升级，则返回权威无更新信息 (hasUpdate = false)
        val latestConfirmed = probeResult.getOrNull()
            ?: cdnResult.getOrNull()
            ?: rawResult.getOrNull()
            ?: apiResult.getOrNull()

        if (latestConfirmed != null) {
            return@withContext Result.success(latestConfirmed.copy(hasUpdate = false))
        }

        val lastErr = cdnResult.exceptionOrNull() ?: probeResult.exceptionOrNull() ?: apiResult.exceptionOrNull()
        Result.failure(Exception("检查更新失败，请稍后重试 (${lastErr?.localizedMessage ?: "网络波动"})"))
    }

    private fun checkViaVersionJson(url: String, currentVersion: String): Result<UpdateInfo> {
        try {
            val cacheBusterUrl = if (url.contains("?")) {
                "$url&_t=${System.currentTimeMillis()}"
            } else {
                "$url?_t=${System.currentTimeMillis()}"
            }
            val req = Request.Builder()
                .url(cacheBusterUrl)
                .addHeader("User-Agent", "JobAssistant-Android")
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .addHeader("Pragma", "no-cache")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("HTTP ${resp.code}"))
            }
            val body = resp.body?.string() ?: ""
            val json = JSONObject(body)
            val latestVersion = json.optString("versionName", "").trim().removePrefix("v").removePrefix("V")
            val downloadUrl = json.optString("downloadUrl", "")
            val releaseNotes = json.optString("releaseNotes", "新版本体验与性能优化")
            if (latestVersion.isBlank() || downloadUrl.isBlank()) {
                return Result.failure(Exception("格式解析异常"))
            }
            val hasUpdate = isNewerVersion(latestVersion, currentVersion)
            return Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    assetSize = 0L
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun checkViaWebRedirect(url: String, currentVersion: String): Result<UpdateInfo> {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .head()
                .build()
            val resp = noRedirectHttpClient.newCall(req).execute()
            val location = resp.header("Location") ?: ""
            if (location.isBlank()) {
                return Result.failure(Exception("未获取到跳转地址"))
            }
            val tag = location.substringAfterLast("/").trim()
            val latestVersion = tag.removePrefix("v").removePrefix("V")
            if (latestVersion.isBlank()) {
                return Result.failure(Exception("版本标识为空"))
            }
            val downloadUrl = "https://github.com/981029l/job-floating-assistant/releases/download/$tag/JobAssistant-$tag.apk"
            val hasUpdate = isNewerVersion(latestVersion, currentVersion)
            return Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    releaseNotes = "发现最新版本 $latestVersion，推荐在应用内一键下载更新以获得最佳体验！",
                    downloadUrl = downloadUrl,
                    assetSize = 0L
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun checkViaGithubApi(url: String, currentVersion: String): Result<UpdateInfo> {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "JobAssistant-Android")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("API HTTP ${resp.code}"))
            }
            val body = resp.body?.string() ?: ""
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val latestVersion = tagName.removePrefix("v").removePrefix("V")
            val releaseNotes = json.optString("body", "暂无更新说明").trim()
            var downloadUrl = ""
            var assetSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        assetSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            val hasUpdate = isNewerVersion(latestVersion, currentVersion) && downloadUrl.isNotBlank()
            return Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    assetSize = assetSize
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val tempFile = File(destDir, "JobAssistant-update.apk.tmp")
        val targetFile = File(destDir, "JobAssistant-update.apk")

        if (tempFile.exists()) tempFile.delete()
        if (targetFile.exists()) targetFile.delete()

        // 优先使用官方直链，国内网络环境若失败则备用镜像通道
        val urlsToTry = listOf(
            downloadUrl,
            "https://ghfast.top/$downloadUrl"
        )

        var lastException: Exception? = null

        for (url in urlsToTry) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "JobAssistant-Android")
                    .get()
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    throw Exception("下载通道响应异常 (${resp.code})")
                }

                val body = resp.body ?: throw Exception("响应体为空")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var accumulated: Long = 0
                        var lastReportedProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            accumulated += bytesRead
                            if (totalBytes > 0) {
                                val progress = (accumulated * 100 / totalBytes).toInt().coerceIn(0, 100)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (renamed || targetFile.exists()) {
                        return@withContext Result.success(targetFile)
                    }
                    return@withContext Result.success(tempFile)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        Result.failure(lastException ?: Exception("APK 下载失败，请检查网络连接"))
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "安装包不存在或损坏，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 8.0+ 检查未知来源安装权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "请在弹出的设置中开启【允许来自此来源的应用】，然后重新点击安装", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "调起系统安装器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()

        if (cleanLatest == cleanCurrent) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
