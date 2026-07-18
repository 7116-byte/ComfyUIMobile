package com.local.comfyuimobile.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "ComfyUIMobile"
    private const val PREFS = "diagnostic_logging"
    private const val ENABLED = "enabled"
    private const val LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val MAX_EXIT_TRACE_BYTES = 128 * 1024
    private val traceKeyword = Regex(
        "fatal|sig[a-z0-9]+|crash|webview|chromium|abort|backtrace|fingerprint|abi|process|pid|tid|signal|tombstone|\\.so\\b",
        RegexOption.IGNORE_CASE,
    )
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
    @Volatile private var context: Context? = null
    @Volatile private var enabled = false
    @Volatile private var installed = false

    fun initialize(value: Context) {
        context = value.applicationContext
        enabled = isEnabled(value)
        installCrashHandler()
        info("应用启动，日志记录=${if (enabled) "开启" else "关闭"}")
        if (enabled) recordHistoricalExits(value)
    }

    fun isEnabled(value: Context): Boolean =
        value.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(value: Context, valueEnabled: Boolean) {
        if (!valueEnabled && enabled) info("用户关闭诊断日志")
        value.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, valueEnabled).apply()
        enabled = valueEnabled
        if (valueEnabled) {
            info("用户开启诊断日志")
            recordHistoricalExits(value)
        }
    }

    fun info(message: String) = write("信息", message)

    fun error(message: String, throwable: Throwable? = null) {
        val detail = if (throwable == null) message else "$message\n${stackTrace(throwable)}"
        write("错误", detail)
    }

    fun read(): String = synchronized(lock) {
        val app = context ?: return@synchronized "日志器尚未初始化"
        val folder = File(app.filesDir, "logs")
        val previous = File(folder, "comfy-mobile.previous.log").takeIf(File::isFile)?.readText().orEmpty()
        val current = File(folder, "comfy-mobile.log").takeIf(File::isFile)?.readText().orEmpty()
        listOf(previous, current)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .let(::compactBinaryTraceForDisplay)
            .ifBlank { "暂无诊断日志" }
    }

    fun clear() = synchronized(lock) {
        val app = context ?: return@synchronized
        val folder = File(app.filesDir, "logs")
        File(folder, "comfy-mobile.log").delete()
        File(folder, "comfy-mobile.previous.log").delete()
    }

    private fun write(level: String, message: String) {
        if (!enabled) return
        if (level == "错误") Log.e(TAG, message) else Log.i(TAG, message)
        synchronized(lock) {
            runCatching {
                val app = context ?: return@runCatching
                val folder = File(app.filesDir, "logs").apply { mkdirs() }
                val file = File(folder, "comfy-mobile.log")
                if (file.length() >= MAX_BYTES) {
                    val previous = File(folder, "comfy-mobile.previous.log")
                    previous.delete()
                    file.renameTo(previous)
                }
                file.appendText("${formatter.format(Date())} [$level] $message\n", Charsets.UTF_8)
            }
        }
    }

    private fun installCrashHandler() {
        if (installed) return
        synchronized(lock) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                error("未捕获闪退，线程=${thread.name}", throwable)
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    private fun recordHistoricalExits(value: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val preferences = value.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastRecorded = preferences.getLong(LAST_EXIT_TIMESTAMP, 0L)
            val exits = value.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(value.packageName, 0, 8)
                .filter { it.timestamp > lastRecorded }
                .sortedBy { it.timestamp }
            exits.forEach { exit ->
                info(
                    "系统历史退出：时间=${formatter.format(Date(exit.timestamp))}，原因=${exitReason(exit.reason)}，" +
                        "进程=${exit.processName.orEmpty()}，PID=${exit.pid}，状态=${exit.status}，" +
                        "重要性=${exit.importance}，PSS=${exit.pss}KB，RSS=${exit.rss}KB，" +
                        "描述=${exit.description.orEmpty()}",
                )
                readExitTrace(exit)?.let { trace ->
                    info("系统退出追踪：进程=${exit.processName.orEmpty()}\n$trace")
                }
            }
            exits.maxOfOrNull { it.timestamp }?.let { newest ->
                preferences.edit().putLong(LAST_EXIT_TIMESTAMP, newest).apply()
            }
        }.onFailure { error("读取系统历史退出原因失败", it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readExitTrace(exit: ApplicationExitInfo): String? = runCatching {
        val stream = exit.traceInputStream ?: return@runCatching null
        val output = ArrayList<Byte>(MAX_EXIT_TRACE_BYTES)
        var truncated = false
        stream.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (output.size < MAX_EXIT_TRACE_BYTES) {
                val remaining = MAX_EXIT_TRACE_BYTES - output.size
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                repeat(count) { output.add(buffer[it]) }
            }
            truncated = input.read() >= 0
        }
        if (output.isEmpty()) return@runCatching null
        val bytes = ByteArray(output.size) { output[it] }
        val readable = extractReadableTrace(bytes)
        if (readable.isBlank()) "二进制追踪中未提取到可读的崩溃信息，共读取 ${bytes.size} 字节"
        else buildString {
            append(readable)
            if (truncated) append("\n……追踪内容过长，已截断……")
        }
    }.getOrElse { throwable ->
        "读取退出追踪失败：${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
    }

    private fun extractReadableTrace(bytes: ByteArray): String {
        val runs = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            current.toString().trim().takeIf { it.length >= 4 }?.let(runs::add)
            current.clear()
        }
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            if (value in 0x20..0x7e || value == '\t'.code) current.append(value.toChar()) else flush()
        }
        flush()
        val distinct = runs.asSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
        val important = distinct.filter(traceKeyword::containsMatchIn)
        return (important.ifEmpty { distinct.take(12) })
            .take(60)
            .joinToString("\n")
            .take(16 * 1024)
    }

    private fun compactBinaryTraceForDisplay(raw: String): String = raw.lineSequence()
        .mapNotNull { line ->
            if (line.count { it == '\uFFFD' } < 3) return@mapNotNull line
            val ascii = line.map { char ->
                if (char.code in 0x20..0x7e || char == '\t') char else ' '
            }.joinToString("").replace(Regex("[ \\t]+"), " ").trim()
            ascii.takeIf(traceKeyword::containsMatchIn)?.take(1_200)
        }
        .joinToString("\n")

    private fun exitReason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Java/Kotlin 闪退"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生闪退"
        ApplicationExitInfo.REASON_ANR -> "无响应"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "内存不足"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源使用过多"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "用户或系统请求停止"
        ApplicationExitInfo.REASON_SIGNALED -> "系统信号终止"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依赖进程终止"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "权限变化"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "软件更新"
        else -> "其他($reason)"
    }

    private fun stackTrace(throwable: Throwable): String = StringWriter().also { writer ->
        throwable.printStackTrace(PrintWriter(writer))
    }.toString()
}
