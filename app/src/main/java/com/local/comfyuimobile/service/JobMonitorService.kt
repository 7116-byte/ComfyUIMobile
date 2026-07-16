package com.local.comfyuimobile.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.local.comfyuimobile.MainActivity
import com.local.comfyuimobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val promptId = intent?.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
        if (intent?.action == ACTION_STOP) {
            monitors.remove(promptId)?.cancel()
            workflowNames.remove(promptId)
            stopIfIdle()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PROGRESS) {
            if (!monitors.containsKey(promptId)) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val percent = intent.getIntExtra(EXTRA_PROGRESS, -1)
            val node = intent.getStringExtra(EXTRA_NODE).orEmpty()
            val name = workflowNames[promptId].orEmpty().ifBlank { "ComfyUI 工作流" }
            startForeground(FOREGROUND_ID, notification("正在生成${if (percent >= 0) " $percent%" else ""}", listOf(name, node).filter { it.isNotBlank() }.joinToString(" · "), true, percent))
            return START_NOT_STICKY
        }
        val baseUrl = intent?.getStringExtra(EXTRA_BASE_URL).orEmpty().trimEnd('/')
        val workflowName = intent?.getStringExtra(EXTRA_WORKFLOW_NAME).orEmpty().ifBlank { "ComfyUI 工作流" }
        if (baseUrl.isBlank() || promptId.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        workflowNames[promptId] = workflowName
        startForeground(FOREGROUND_ID, notification("正在生成", workflowName, true))
        monitors.remove(promptId)?.cancel()
        val monitor = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                runCatching { readStatus(baseUrl, promptId) }.onSuccess { status ->
                    if (status.completed) {
                        val title = if (status.error) "生成失败" else "生成完成"
                        getSystemService(NotificationManager::class.java)
                            .notify(promptId.hashCode(), notification(title, workflowName, false))
                        monitors.remove(promptId)
                        workflowNames.remove(promptId)
                        stopIfIdle()
                        return@launch
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

    private fun stopIfIdle() {
        if (monitors.isNotEmpty()) {
            val name = workflowNames.values.firstOrNull().orEmpty().ifBlank { "ComfyUI 工作流" }
            startForeground(FOREGROUND_ID, notification("正在生成", name, true))
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(title: String, text: String, ongoing: Boolean, progress: Int = -1): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(ongoing)
            .apply { if (ongoing) setProgress(100, progress.coerceIn(0, 100), progress < 0) }
            .build()
    }

    private data class PollStatus(val completed: Boolean, val error: Boolean)

    companion object {
        const val CHANNEL_ID = "comfy_jobs"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_PROMPT_ID = "prompt_id"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_NODE = "node"
        const val ACTION_PROGRESS = "com.local.comfyuimobile.action.PROGRESS"
        const val ACTION_STOP = "com.local.comfyuimobile.action.STOP_MONITOR"
        private const val FOREGROUND_ID = 8188
    }
}
