package com.local.comfyuimobile.data

import android.content.Context
import android.net.Uri
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ResultSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalResultCache(context: Context) {
    private val root = File(context.filesDir, "result_cache")
    private val indexFile = File(root, "index.json")
    private val mutex = Mutex()

    suspend fun load(): List<ResultMedia> = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex().mapNotNull(::decodeRecord).sortedByDescending { it.createdAt } }
    }

    suspend fun contains(media: ResultMedia): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = key(media)
            readIndex().any { it.optString("key") == key && File(it.optString("localPath")).isFile }
        }
    }

    fun destination(media: ResultMedia): File {
        val extension = media.filename.substringAfterLast('.', "bin").take(12)
        val folder = File(root, safe(media.jobId))
        return File(folder, "${safe(media.nodeId)}-${key(media).hashCode().toUInt()}.$extension")
    }

    suspend fun add(media: ResultMedia, file: File): ResultMedia = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = key(media)
            val records = readIndex().filterNot { it.optString("key") == key }.toMutableList()
            records += encodeRecord(media, file, key)
            writeIndex(records)
            media.copy(
                url = Uri.fromFile(file).toString(),
                source = ResultSource.LOCAL,
                localPath = file.absolutePath,
            )
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (root.exists()) root.deleteRecursively()
        }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun readIndex(): List<JSONObject> = runCatching {
        val array = JSONArray(indexFile.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty().ifBlank { "[]" })
        List(array.length()) { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeIndex(records: List<JSONObject>) {
        root.mkdirs()
        val temporary = File(root, "index.tmp")
        temporary.writeText(JSONArray(records).toString(), Charsets.UTF_8)
        if (indexFile.exists()) indexFile.delete()
        check(temporary.renameTo(indexFile)) { "无法更新本地缓存索引" }
    }

    private fun encodeRecord(media: ResultMedia, file: File, key: String) = JSONObject()
        .put("key", key)
        .put("jobId", media.jobId)
        .put("nodeId", media.nodeId)
        .put("filename", media.filename)
        .put("subfolder", media.subfolder)
        .put("type", media.type)
        .put("kind", media.kind.name)
        .put("createdAt", media.createdAt)
        .put("taskNumber", media.taskNumber)
        .put("workflowPath", media.workflowPath)
        .put("workflowName", media.workflowName)
        .put("localPath", file.absolutePath)

    private fun decodeRecord(item: JSONObject): ResultMedia? {
        val file = File(item.optString("localPath"))
        if (!file.isFile) return null
        return ResultMedia(
            jobId = item.optString("jobId"),
            nodeId = item.optString("nodeId"),
            filename = item.optString("filename"),
            subfolder = item.optString("subfolder"),
            type = item.optString("type"),
            kind = runCatching { MediaKind.valueOf(item.optString("kind")) }.getOrDefault(MediaKind.IMAGE),
            url = Uri.fromFile(file).toString(),
            createdAt = item.optLong("createdAt", file.lastModified()),
            taskNumber = item.optLong("taskNumber"),
            workflowPath = item.optString("workflowPath"),
            workflowName = item.optString("workflowName"),
            source = ResultSource.LOCAL,
            localPath = file.absolutePath,
        )
    }

    private fun key(media: ResultMedia): String =
        listOf(media.jobId, media.nodeId, media.type, media.subfolder, media.filename).joinToString("/")

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "item" }
}
