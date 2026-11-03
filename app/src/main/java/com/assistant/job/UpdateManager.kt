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
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/981029l/job-floating-assistant/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .addHeader("User-Agent", "JobAssistant-Android")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("检查更新失败 (${response.code})"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val latestVersion = if (tagName.startsWith("v", ignoreCase = true)) {
                tagName.substring(1)
            } else {
                tagName
            }

            val releaseNotes = json.optString("body", "暂无更新日志说明").trim()

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

            val currentVersion = getCurrentVersionName(context)
            val hasUpdate = isNewerVersion(latestVersion, currentVersion) && downloadUrl.isNotBlank()

            Result.success(
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
            Result.failure(e)
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
