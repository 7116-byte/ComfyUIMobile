package com.local.comfyuimobile.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.local.comfyuimobile.BuildConfig
import com.local.comfyuimobile.model.UpdateInfo
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val apiBase = "https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest"
        val root = fastestSuccessful(UpdateMirrors.apiCandidates(apiBase)) { url ->
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .get()
                        .build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("检查失败：HTTP ${response.code}")
                    JSONObject(response.body?.string().orEmpty())
                }
            }.getOrNull()
        } ?: return@withContext null
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

    /** Downloads inside this App, validates the APK, then opens Android's package installer. */
    suspend fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): UpdateInstallResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            throw IllegalStateException("请允许本应用安装未知应用，然后重新点击下载并安装")
        }
        val shaUrl = requireNotNull(info.sha256Url) { "Release 缺少 SHA-256 校验文件" }
        val probes = coroutineScope {
            UpdateMirrors.candidates(info.apkUrl, shaUrl).map { candidate ->
                async { probeCandidate(candidate) }
            }.mapNotNull { it.await() }
        }
        val selected = UpdateMirrors.pickFastest(probes)
            ?: throw IllegalStateException("国内镜像和 GitHub 原地址均无法连接")
        val candidate = selected.candidate
        val expectedSha = selected.expectedSha
        val filename = "ComfyUIMobile-${info.tag}-release.apk"
        val directory = File(context.cacheDir, UPDATE_DIRECTORY)
        UpdateFiles.prepareInternalDirectory(directory)
        UpdateFiles.cleanLegacyDirectory(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val partial = File(directory, "$filename.part")
        val completed = File(directory, filename)
        var installerStarted = false
        try {
            val actualSha = download(candidate.apkUrl, partial, onProgress)
            require(actualSha.equals(expectedSha, true)) { "APK SHA-256 校验失败" }
            require(partial.isFile && partial.length() > 0L) { "更新 APK 下载不完整" }
            if (completed.exists() && !completed.delete()) error("无法替换旧更新文件")
            check(partial.renameTo(completed)) { "无法完成更新文件写入" }
            verifyPackage(completed)
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.Main) { launchInstaller(completed) }
            installerStarted = true
            UpdateInstallResult(candidate.label, selected.latencyMillis, completed.length())
        } finally {
            partial.delete()
            if (!installerStarted) completed.delete()
        }
    }

    /** Removes packages left by the old DownloadManager implementation after upgrading. */
    suspend fun cleanupObsoletePackages() = withContext(Dispatchers.IO) {
        UpdateFiles.cleanInternalDirectory(File(context.cacheDir, UPDATE_DIRECTORY))
        UpdateFiles.cleanLegacyDirectory(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        cleanupLegacyDownloadManagerRecords()
    }

    private suspend fun download(
        url: String,
        destination: File,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache, no-transform")
            .get()
            .build()
        return executeCancellable(request) { response ->
            if (response.code != 200 || response.header("Content-Range") != null) {
                throw IOException("更新下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("更新下载内容为空")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastPublishedAt = 0L
            onProgress(UpdateDownloadProgress(0L, total))
            destination.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (total >= 0L && downloaded > total) throw IOException("更新文件超过声明长度")
                        val now = System.nanoTime()
                        if (now - lastPublishedAt >= PROGRESS_INTERVAL_NANOS) {
                            onProgress(UpdateDownloadProgress(downloaded, total))
                            lastPublishedAt = now
                        }
                    }
                }
            }
            if (total >= 0L && downloaded != total) {
                throw IOException("更新下载不完整：$downloaded / $total 字节")
            }
            onProgress(UpdateDownloadProgress(downloaded, total))
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private suspend fun <T> executeCancellable(request: Request, block: suspend (Response) -> T): T = coroutineScope {
        val call: Call = client.newCall(request)
        val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            currentCoroutineContext().ensureActive()
            call.execute().use { response -> block(response) }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellation.cancel()
        }
    }

    private suspend fun probeCandidate(candidate: UpdateDownloadCandidate): MirrorProbe? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                val text = client.newCall(
                    Request.Builder().url(candidate.sha256Url).get().build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("校验文件下载失败：HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
                val expectedSha = text.trim().substringBefore(' ')
                    .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                    ?: return@runCatching null
                MirrorProbe(candidate, (System.nanoTime() - start) / 1_000_000, expectedSha)
            }.getOrNull()
        }

    private suspend fun <T> fastestSuccessful(
        urls: List<String>,
        load: suspend (String) -> T?,
    ): T? = coroutineScope {
        urls.map { url ->
            async {
                val start = System.nanoTime()
                load(url)?.let { (System.nanoTime() - start) to it }
            }
        }.mapNotNull { it.await() }
            .minByOrNull { it.first }
            ?.second
    }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun cleanupLegacyDownloadManagerRecords() {
        runCatching {
            val manager = context.getSystemService(DownloadManager::class.java)
            val ids = mutableListOf<Long>()
            manager.query(DownloadManager.Query()).use { cursor ->
                val idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                while (cursor.moveToNext()) {
                    val title = titleColumn.takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
                    val localName = uriColumn.takeIf { it >= 0 }
                        ?.let(cursor::getString)
                        ?.let { Uri.parse(it).lastPathSegment }
                        .orEmpty()
                    if (UpdateFiles.isManagedPackageName(title) || UpdateFiles.isManagedPackageName(localName)) {
                        ids += cursor.getLong(idColumn)
                    }
                }
            }
            if (ids.isNotEmpty()) manager.remove(*ids.toLongArray())
            context.getSharedPreferences("update_download", Context.MODE_PRIVATE).edit().clear().apply()
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

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val UPDATE_DIRECTORY = "updates"
        private const val PROGRESS_INTERVAL_NANOS = 100_000_000L
    }
}

internal object UpdateFiles {
    private val managedPackage = Regex("ComfyUIMobile-v[0-9A-Za-z._-]+-release\\.apk(?:\\.part|\\.sha256)?", RegexOption.IGNORE_CASE)

    fun prepareInternalDirectory(directory: File) {
        cleanInternalDirectory(directory)
        if (!directory.isDirectory && !directory.mkdirs()) error("无法创建应用内更新目录")
    }

    fun cleanInternalDirectory(directory: File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }

    fun cleanLegacyDirectory(directory: File?) {
        directory?.listFiles()
            ?.filter { it.isFile && isManagedPackageName(it.name) }
            ?.forEach { it.delete() }
    }

    fun isManagedPackageName(name: String): Boolean = managedPackage.matches(name)
}

data class UpdateInstallResult(val source: String, val latencyMillis: Long, val bytes: Long)

data class UpdateDownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
    val fraction: Float?
        get() = totalBytes.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

data class UpdateDownloadCandidate(
    val label: String,
    val apkUrl: String,
    val sha256Url: String,
)

data class MirrorProbe(
    val candidate: UpdateDownloadCandidate,
    val latencyMillis: Long,
    val expectedSha: String,
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

    fun apiCandidates(baseUrl: String): List<String> = buildList {
        mirrors.forEach { (_, prefix) -> add(prefix + baseUrl) }
        add(baseUrl)
    }

    fun pickFastest(probes: List<MirrorProbe>): MirrorProbe? = probes.minByOrNull { it.latencyMillis }
}
