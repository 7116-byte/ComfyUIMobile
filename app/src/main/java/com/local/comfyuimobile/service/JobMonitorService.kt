package com.local.comfyuimobile.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.local.comfyuimobile.MainActivity
import com.local.comfyuimobile.R
import com.local.comfyuimobile.data.AppPreferences
import com.local.comfyuimobile.data.AppLogger
import com.local.comfyuimobile.data.CachePolicy
import com.local.comfyuimobile.data.LocalResultCache
import com.local.comfyuimobile.network.ComfyClient
import com.local.comfyuimobile.network.ResultParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class JobMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val monitors = ConcurrentHashMap<String, Job>()
    private val workflowNames = ConcurrentHashMap<String, String>()
    private val workflowPaths = ConcurrentHashMap<String, String>()
    private val serverUrls = ConcurrentHashMap<String, String>()
    private val localResultCache by lazy { LocalResultCache(applicationContext) }
    private val preferences by lazy { AppPreferences(applicationContext) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:comfy-job").apply {
            setReferenceCounted(false)
        }
    }
    private val wifiLock by lazy {
        getSystemService(WifiManager::class.java).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:comfy-job").apply {
            setReferenceCounted(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val promptId = intent?.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
        val workflowName = intent?.getStringExtra(EXTRA_WORKFLOW_NAME).orEmpty().ifBlank {
            workflowNames[promptId].orEmpty().ifBlank { "ComfyUI 工作流" }
        }
        val workflowPath = intent?.getStringExtra(EXTRA_WORKFLOW_PATH).orEmpty().ifBlank {
            workflowPaths[promptId].orEmpty()
        }
        val baseUrl = intent?.getStringExtra(EXTRA_BASE_URL).orEmpty().ifBlank {
            serverUrls[promptId].orEmpty()
        }
        return try {
            // startForegroundService() 启动后必须立刻建立前台通知。日志、锁和任务恢复均放在其后，
            // 避免系统在进程繁忙或锁获取变慢时抛出 ForegroundServiceDidNotStartInTimeException。
            startForeground(
                FOREGROUND_ID,
                notification("正在准备后台任务", workflowName, true, promptId = promptId, baseUrl = baseUrl, workflowPath = workflowPath),
            )
            AppLogger.info("后台前台通知已建立：任务=${promptId.ifBlank { "待恢复" }}")
            handleStartCommand(intent, startId)
        } catch (error: Throwable) {
            AppLogger.error("后台任务服务启动失败，任务=${promptId.ifBlank { "未知" }}", error)
            monitors.remove(promptId)?.cancel()
            workflowNames.remove(promptId)
            workflowPaths.remove(promptId)
            serverUrls.remove(promptId)
            runCatching { releaseBackgroundLocks() }
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private fun handleStartCommand(intent: Intent?, startId: Int): Int {
        val promptId = intent?.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
        if (intent?.action == ACTION_STOP) {
            monitors.remove(promptId)?.cancel()
            workflowNames.remove(promptId)
            workflowPaths.remove(promptId)
            serverUrls.remove(promptId)
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PROGRESS) {
            if (!monitors.containsKey(promptId)) {
                stopIfIdle()
                return START_NOT_STICKY
            }
            val percent = intent.getIntExtra(EXTRA_PROGRESS, -1)
            val node = intent.getStringExtra(EXTRA_NODE).orEmpty()
            val name = workflowNames[promptId].orEmpty().ifBlank { "ComfyUI 工作流" }
            startForeground(
                FOREGROUND_ID,
                notification(
                    "正在生成${if (percent >= 0) " $percent%" else ""}",
                    listOf(name, node).filter { it.isNotBlank() }.joinToString(" · "),
                    ongoing = true,
                    progress = percent,
                    promptId = promptId,
                    baseUrl = serverUrls[promptId].orEmpty(),
                    workflowPath = workflowPaths[promptId].orEmpty(),
                ),
            )
            return START_NOT_STICKY
        }
        val baseUrl = intent?.getStringExtra(EXTRA_BASE_URL).orEmpty().trimEnd('/')
        val workflowName = intent?.getStringExtra(EXTRA_WORKFLOW_NAME).orEmpty().ifBlank { "ComfyUI 工作流" }
        val workflowPath = intent?.getStringExtra(EXTRA_WORKFLOW_PATH).orEmpty()
        if (baseUrl.isBlank() || promptId.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        workflowNames[promptId] = workflowName
        workflowPaths[promptId] = workflowPath
        serverUrls[promptId] = baseUrl
        AppLogger.info("后台开始监控任务：$promptId，工作流=$workflowName")
        startForeground(
            FOREGROUND_ID,
            notification("正在生成", workflowName, true, promptId = promptId, baseUrl = baseUrl, workflowPath = workflowPath),
        )
        holdBackgroundLocks()
        monitors.remove(promptId)?.cancel()
        val monitor = scope.launch(start = CoroutineStart.LAZY) {
            var consecutivePollFailures = 0
            while (isActive) {
                runCatching { readStatus(baseUrl, promptId) }.onSuccess { status ->
                    consecutivePollFailures = 0
                    if (status.completed) {
                        if (status.error) {
                            getSystemService(NotificationManager::class.java)
                                .notify(
                                    promptId.hashCode(),
                                    completionNotification(
                                        "生成失败",
                                        workflowName,
                                        promptId,
                                        baseUrl,
                                        workflowPath,
                                    ),
                                )
                        } else {
                            val localSaveRequested = runCatching { hasLocalSaveRequested(baseUrl) }.getOrDefault(false)
                            startForeground(
                                FOREGROUND_ID,
                                notification(
                                    "正在整理并保存本地作品",
                                    workflowName,
                                    ongoing = true,
                                    promptId = promptId,
                                    baseUrl = baseUrl,
                                    workflowPath = workflowPath,
                                ),
                            )
                            var report = SaveReport(
                                total = 0,
                                failed = 1,
                                localSaveRequested = localSaveRequested,
                                detail = "尚未开始保存",
                            )
                            for (attempt in 0 until 12) {
                                report = runCatching { saveLocalOutputs(baseUrl, promptId) }
                                    .getOrElse {
                                        SaveReport(
                                            total = 0,
                                            failed = 1,
                                            localSaveRequested = localSaveRequested,
                                            detail = it.message.orEmpty(),
                                        )
                                    }
                                if (report.failed == 0) break
                                if (attempt < 11) {
                                    startForeground(
                                        FOREGROUND_ID,
                                        notification(
                                            "本地保存未完成，正在重试 ${attempt + 1}/12",
                                            workflowName,
                                            ongoing = true,
                                            promptId = promptId,
                                            baseUrl = baseUrl,
                                            workflowPath = workflowPath,
                                        ),
                                    )
                                    delay(minOf(30_000L, (attempt + 1) * 2_000L))
                                }
                            }
                            val savedCount = (report.total - report.failed).coerceAtLeast(0)
                            val title = JobNotificationNavigation.completionTitle(
                                localSaveRequested = report.localSaveRequested,
                                savedCount = savedCount,
                                failed = report.failed > 0,
                            )
                            AppLogger.info("后台任务完成：$promptId，总输出=${report.total}，失败=${report.failed}，详情=${report.detail}")
                            val detail = if (report.failed == 0) workflowName else listOf(workflowName, report.detail.ifBlank { "${report.failed} 项保存失败" }).joinToString(" · ")
                            getSystemService(NotificationManager::class.java)
                                .notify(
                                    promptId.hashCode(),
                                    completionNotification(title, detail, promptId, baseUrl, workflowPath),
                                )
                            sendBroadcast(
                                Intent(ACTION_LOCAL_RESULTS_UPDATED)
                                    .setPackage(packageName)
                                    .putExtra(EXTRA_SAVED_COUNT, savedCount)
                                    .putExtra(EXTRA_SAVE_FAILED, report.failed > 0)
                                    .putExtra(EXTRA_LOCAL_SAVE_REQUESTED, report.localSaveRequested),
                            )
                        }
                        monitors.remove(promptId)
                        workflowNames.remove(promptId)
                        workflowPaths.remove(promptId)
                        serverUrls.remove(promptId)
                        stopIfIdle()
                        return@launch
                    }
                }.onFailure { error ->
                    consecutivePollFailures += 1
                    if (consecutivePollFailures == 1 || consecutivePollFailures % 6 == 0) {
                        AppLogger.error("后台轮询任务失败：$promptId，连续失败=$consecutivePollFailures", error)
                    }
                }
                delay(5_000)
            }
        }
        monitors[promptId] = monitor
        monitor.start()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        releaseBackgroundLocks()
        scope.cancel()
        super.onDestroy()
    }

    private fun readStatus(baseUrl: String, promptId: String): PollStatus {
        val encoded = URLEncoder.encode(promptId, Charsets.UTF_8.name())
        val request = Request.Builder().url("$baseUrl/history/$encoded").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return PollStatus(false, false)
            val root = JSONObject(response.body?.string().orEmpty())
            val status = root.optJSONObject(promptId)?.optJSONObject("status") ?: return PollStatus(false, false)
            return PollStatus(
                completed = status.optBoolean("completed"),
                error = status.optString("status_str").equals("error", true),
            )
        }
    }

    private suspend fun saveLocalOutputs(baseUrl: String, promptId: String): SaveReport {
        val resultClient = ComfyClient()
        resultClient.setServer(baseUrl)
        val history = resultClient.history(promptId)
        check(history.optJSONObject(promptId) != null) { "任务结果尚未写入历史" }
        val settings = preferences.settings.first()
        val localSaveRequested = settings.cacheOutputRules.any { rule ->
            rule.enabled && rule.serverUrl == baseUrl
        }
        val eligible = ResultParser.parse(baseUrl, history).filter { media ->
            media.jobId == promptId && CachePolicy.shouldCache(
                media,
                settings.submittedJobs,
                settings.cacheOutputRules,
                baseUrl,
                settings.cacheClearedAt,
            )
        }
        if (localSaveRequested && eligible.isEmpty()) {
            return SaveReport(
                total = 0,
                failed = 1,
                localSaveRequested = true,
                detail = "尚未读取到白名单输出",
            )
        }
        var failed = 0
        var lastError = ""
        for (media in eligible) {
            if (localResultCache.contains(media)) continue
            val destination = localResultCache.destination(media)
            var saved = false
            repeat(3) { attempt ->
                if (saved) return@repeat
                runCatching {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> resultClient.downloadTo(media.url, output) }
                    localResultCache.add(media, destination)
                }.onSuccess {
                    saved = true
                }.onFailure { error ->
                    lastError = error.message.orEmpty()
                    destination.delete()
                    if (attempt < 2) delay((attempt + 1) * 1_000L)
                }
            }
            if (!saved) failed += 1
        }
        return SaveReport(
            total = eligible.size,
            failed = failed,
            localSaveRequested = localSaveRequested,
            detail = lastError,
        )
    }

    private fun stopIfIdle() {
        if (monitors.isNotEmpty()) {
            val promptId = monitors.keys.firstOrNull().orEmpty()
            val name = workflowNames[promptId].orEmpty().ifBlank { "ComfyUI 工作流" }
            startForeground(
                FOREGROUND_ID,
                notification(
                    "正在生成",
                    name,
                    ongoing = true,
                    promptId = promptId,
                    baseUrl = serverUrls[promptId].orEmpty(),
                    workflowPath = workflowPaths[promptId].orEmpty(),
                ),
            )
            return
        }
        releaseBackgroundLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun holdBackgroundLocks() {
        if (!wakeLock.isHeld) wakeLock.acquire()
        if (!wifiLock.isHeld) wifiLock.acquire()
    }

    private fun releaseBackgroundLocks() {
        if (wifiLock.isHeld) wifiLock.release()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun notification(
        title: String,
        text: String,
        ongoing: Boolean,
        progress: Int = -1,
        promptId: String = "",
        baseUrl: String = "",
        workflowPath: String = "",
    ): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(promptId, baseUrl, workflowPath, completed = false))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .apply { if (ongoing) setProgress(100, progress.coerceIn(0, 100), progress < 0) }
            .build()
    }

    private suspend fun hasLocalSaveRequested(baseUrl: String): Boolean =
        preferences.settings.first().cacheOutputRules.any { rule ->
            rule.enabled && rule.serverUrl == baseUrl
        }

    private fun completionNotification(
        title: String,
        text: String,
        promptId: String,
        baseUrl: String,
        workflowPath: String,
    ): Notification = Notification.Builder(this, COMPLETION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent(promptId, baseUrl, workflowPath, completed = true))
        .setCategory(Notification.CATEGORY_STATUS)
        .setAutoCancel(true)
        .build()

    private fun contentIntent(
        promptId: String,
        baseUrl: String,
        workflowPath: String,
        completed: Boolean,
    ): PendingIntent {
        val contentIntent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_JOB)
            .setData(Uri.parse("comfyuimobile://job/${Uri.encode(promptId.ifBlank { "current" })}"))
            .putExtra(EXTRA_PROMPT_ID, promptId)
            .putExtra(EXTRA_BASE_URL, baseUrl)
            .putExtra(EXTRA_WORKFLOW_PATH, workflowPath)
            .putExtra(EXTRA_OPEN_COMPLETED, completed)
            .addFlags(JobNotificationNavigation.activityFlags)
        val pendingIntent = PendingIntent.getActivity(
            this,
            JobNotificationNavigation.requestCode(promptId),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return pendingIntent
    }

    private data class PollStatus(val completed: Boolean, val error: Boolean)
    private data class SaveReport(
        val total: Int,
        val failed: Int,
        val localSaveRequested: Boolean = false,
        val detail: String = "",
    )

    companion object {
        const val CHANNEL_ID = "comfy_jobs"
        const val COMPLETION_CHANNEL_ID = "comfy_job_completion_v1"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_PROMPT_ID = "prompt_id"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_WORKFLOW_PATH = "workflow_path"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_NODE = "node"
        const val ACTION_PROGRESS = "com.local.comfyuimobile.action.PROGRESS"
        const val ACTION_STOP = "com.local.comfyuimobile.action.STOP_MONITOR"
        const val ACTION_LOCAL_RESULTS_UPDATED = "com.local.comfyuimobile.action.LOCAL_RESULTS_UPDATED"
        const val ACTION_OPEN_JOB = "com.local.comfyuimobile.action.OPEN_JOB"
        const val EXTRA_SAVED_COUNT = "saved_count"
        const val EXTRA_SAVE_FAILED = "save_failed"
        const val EXTRA_LOCAL_SAVE_REQUESTED = "local_save_requested"
        const val EXTRA_OPEN_COMPLETED = "open_completed"
        private const val FOREGROUND_ID = 8188
    }
}
