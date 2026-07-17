package com.local.comfyuimobile.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.local.comfyuimobile.BuildConfig
import com.local.comfyuimobile.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val state = context.getSharedPreferences("update_download", Context.MODE_PRIVATE)

    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get().build()
        val root = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GitHub 检查失败：HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return@withContext null
        val tag = root.optString("tag_name")
        if (tag.isBlank() || VersionComparator.compare(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext null
        val expectedApkName = "ComfyUIMobile-$tag-release.apk"
        val expectedShaName = "$expectedApkName.sha256"
        val assets = root.optJSONArray("assets") ?: return@withContext null
        var apkUrl = ""
        var shaUrl: String? = null
        repeat(assets.length()) { index ->
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.equals(expectedApkName, true)) apkUrl = url
            if (name.equals(expectedShaName, true)) shaUrl = url
        }
        if (apkUrl.isBlank() || shaUrl.isNullOrBlank()) return@withContext null
        UpdateInfo(tag, apkUrl, shaUrl, root.optString("html_url"))
    }

    suspend fun enqueue(info: UpdateInfo): UpdateEnqueueResult = withContext(Dispatchers.IO) {
        val shaUrl = requireNotNull(info.sha256Url) { "Release 缺少 SHA-256 校验文件" }
        val selected = UpdateMirrors.candidates(info.apkUrl, shaUrl).firstNotNullOfOrNull { candidate ->
            runCatching {
                val expectedSha = downloadText(candidate.sha256Url).trim().substringBefore(' ')
                expectedSha.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.let { candidate to it }
            }.getOrNull()
        } ?: throw IllegalStateException("国内镜像和 GitHub 原地址均无法连接")
        val (candidate, expectedSha) = selected
        val filename = "ComfyUIMobile-${info.tag}-release.apk"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("无法访问应用下载目录")
        val file = File(dir, filename).apply { delete() }
        val request = DownloadManager.Request(Uri.parse(candidate.apkUrl))
            .setTitle(filename)
            .setDescription("ComfyUI Mobile 更新 · ${candidate.label}")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        state.edit()
            .putLong("id", id)
            .putString("file", file.absolutePath)
            .putString("sha", expectedSha)
            .putString("tag", info.tag)
            .putString("source", candidate.label)
            .apply()
        UpdateEnqueueResult(id, candidate.label)
    }

    suspend fun verifyAndInstall(downloadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(downloadId == state.getLong("id", -1L)) { "不是本应用发起的更新下载" }
            val manager = context.getSystemService(DownloadManager::class.java)
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                require(cursor.moveToFirst()) { "找不到更新下载任务" }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                require(status == DownloadManager.STATUS_SUCCESSFUL) { "更新下载未成功" }
            }
            val file = File(state.getString("file", null) ?: error("更新文件路径缺失"))
            require(file.isFile) { "更新 APK 不存在" }
            val expectedSha = state.getString("sha", "").orEmpty()
            if (expectedSha.isNotBlank()) require(sha256(file).equals(expectedSha, true)) { "APK SHA-256 校验失败" }
            verifyPackage(file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { context.startActivity(intent) }
        }
    }

    private fun verifyPackage(file: File) {
        val pm = context.packageManager
        val archive = packageInfo(pm, file.absolutePath) ?: error("无法读取更新 APK 信息")
        val installed = packageInfo(pm, context.packageName) ?: error("无法读取当前应用签名")
        UpdateVerifier.verifyMetadata(
            expectedPackage = context.packageName,
            actualPackage = archive.packageName,
            installedVersionCode = longVersion(installed),
            archiveVersionCode = longVersion(archive),
            installedCertificate = signatures(installed),
            archiveCertificate = signatures(archive),
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, pathOrPackage: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (File(pathOrPackage).isFile) pm.getPackageArchiveInfo(pathOrPackage, flags) else pm.getPackageInfo(pathOrPackage, flags)
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): ByteArray {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = requireNotNull(info.signingInfo) { "APK 缺少签名信息" }
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else requireNotNull(info.signatures) { "APK 缺少签名信息" }
        return signatures.map { it.toByteArray().toList() }.flatten().toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun longVersion(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadText(url: String): String = client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("校验文件下载失败：HTTP ${response.code}")
        response.body?.string().orEmpty()
    }

    companion object {
        const val CHANNEL_ID = "comfy_updates"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

data class UpdateEnqueueResult(val downloadId: Long, val source: String)

data class UpdateDownloadCandidate(
    val label: String,
    val apkUrl: String,
    val sha256Url: String,
)

object UpdateMirrors {
    private val mirrors = listOf(
        "国内节点 ghfast" to "https://ghfast.top/",
        "国内节点 ghproxy" to "https://ghproxy.net/",
    )

    fun candidates(apkUrl: String, sha256Url: String): List<UpdateDownloadCandidate> = buildList {
        mirrors.forEach { (label, prefix) ->
            add(UpdateDownloadCandidate(label, prefix + apkUrl, prefix + sha256Url))
        }
        add(UpdateDownloadCandidate("GitHub 原地址", apkUrl, sha256Url))
    }
}
