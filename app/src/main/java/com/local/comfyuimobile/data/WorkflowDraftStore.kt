package com.local.comfyuimobile.data

import android.content.Context
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class WorkflowDraftField(
    val key: String,
    val valueJson: String,
    val displayValue: String,
    val label: String,
    val visible: Boolean,
    val section: ParameterSection,
    val order: Int,
)

data class WorkflowDraft(
    val serverUrl: String,
    val workflowPath: String,
    val workflowName: String,
    val baseModified: Double,
    val workflowJson: String,
    val fields: List<WorkflowDraftField>,
    val updatedAt: Long = System.currentTimeMillis(),
)

object WorkflowDraftFields {
    fun capture(fields: List<ParameterField>): List<WorkflowDraftField> = fields.map { field ->
        WorkflowDraftField(
            key = field.key,
            valueJson = field.valueJson,
            displayValue = field.displayValue,
            label = field.label,
            visible = field.visible,
            section = field.section,
            order = field.order,
        )
    }

    fun restore(fields: List<ParameterField>, draftFields: List<WorkflowDraftField>): List<ParameterField> {
        val saved = draftFields.associateBy { it.key }
        return fields.map { field ->
            val draft = saved[field.key] ?: return@map field
            field.copy(
                valueJson = draft.valueJson,
                displayValue = draft.displayValue,
                label = draft.label.ifBlank { field.label },
                visible = draft.visible,
                section = draft.section,
                order = draft.order,
            )
        }
    }
}

/**
 * Stores the App's unsaved workflow working copies in private app storage.
 * Draft identity is scoped by both the normalized server URL and workflow path.
 */
class WorkflowDraftStore internal constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, DIRECTORY_NAME))

    private val mutex = Mutex()

    suspend fun load(serverUrl: String, workflowPath: String): WorkflowDraft? = withContext(Dispatchers.IO) {
        mutex.withLock { loadNow(serverUrl, workflowPath) }
    }

    suspend fun save(draft: WorkflowDraft) = withContext(Dispatchers.IO) {
        mutex.withLock { saveNow(draft) }
    }

    suspend fun delete(serverUrl: String, workflowPath: String) = withContext(Dispatchers.IO) {
        mutex.withLock { Files.deleteIfExists(fileFor(serverUrl, workflowPath).toPath()) }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        mutex.withLock { countNow() }
    }

    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = directory.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
            files.forEach { file -> runCatching { file.delete() } }
            files.count { !it.exists() }
        }
    }

    internal fun loadNow(serverUrl: String, workflowPath: String): WorkflowDraft? {
        val file = fileFor(serverUrl, workflowPath)
        if (!file.isFile) return null
        return runCatching { decode(file.readText(Charsets.UTF_8)) }
            .getOrNull()
            ?.takeIf {
                normalizeServer(it.serverUrl) == normalizeServer(serverUrl) &&
                    it.workflowPath == workflowPath
            }
    }

    internal fun saveNow(draft: WorkflowDraft) {
        require(draft.serverUrl.isNotBlank()) { "草稿缺少服务器地址" }
        require(draft.workflowPath.isNotBlank()) { "草稿缺少工作流路径" }
        require(draft.workflowJson.isNotBlank()) { "草稿缺少工作流内容" }
        directory.mkdirs()
        val target = fileFor(draft.serverUrl, draft.workflowPath)
        val temporary = File(directory, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(encode(draft))
                writer.flush()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target.setLastModified(draft.updatedAt)
            pruneNow()
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    internal fun deleteNow(serverUrl: String, workflowPath: String) {
        Files.deleteIfExists(fileFor(serverUrl, workflowPath).toPath())
    }

    internal fun countNow(): Int = directory.listFiles { file -> file.isFile && file.extension == "json" }?.size ?: 0

    private fun pruneNow() {
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_DRAFTS)
            .forEach(File::delete)
    }

    private fun fileFor(serverUrl: String, workflowPath: String): File =
        File(directory, "${identity(serverUrl, workflowPath)}.json")

    private fun encode(draft: WorkflowDraft): String = JSONObject()
        .put("schema", SCHEMA)
        .put("serverUrl", normalizeServer(draft.serverUrl))
        .put("workflowPath", draft.workflowPath)
        .put("workflowName", draft.workflowName)
        .put("baseModified", draft.baseModified)
        .put("workflowJson", draft.workflowJson)
        .put("updatedAt", draft.updatedAt)
        .put(
            "fields",
            JSONArray().apply {
                draft.fields.forEach { field ->
                    put(
                        JSONObject()
                            .put("key", field.key)
                            .put("valueJson", field.valueJson)
                            .put("displayValue", field.displayValue)
                            .put("label", field.label)
                            .put("visible", field.visible)
                            .put("section", field.section.name)
                            .put("order", field.order),
                    )
                }
            },
        )
        .toString()

    private fun decode(raw: String): WorkflowDraft {
        val root = JSONObject(raw)
        require(root.optInt("schema") == SCHEMA) { "不支持的草稿格式" }
        val fields = root.optJSONArray("fields") ?: JSONArray()
        return WorkflowDraft(
            serverUrl = root.getString("serverUrl"),
            workflowPath = root.getString("workflowPath"),
            workflowName = root.optString("workflowName"),
            baseModified = root.optDouble("baseModified"),
            workflowJson = root.getString("workflowJson"),
            fields = List(fields.length()) { index ->
                val item = fields.getJSONObject(index)
                WorkflowDraftField(
                    key = item.getString("key"),
                    valueJson = item.getString("valueJson"),
                    displayValue = item.optString("displayValue"),
                    label = item.optString("label"),
                    visible = item.optBoolean("visible", true),
                    section = runCatching { ParameterSection.valueOf(item.optString("section")) }
                        .getOrDefault(ParameterSection.MORE),
                    order = item.optInt("order"),
                )
            },
            updatedAt = root.optLong("updatedAt"),
        )
    }

    internal companion object {
        const val MAX_DRAFTS = 50
        private const val SCHEMA = 1
        private const val DIRECTORY_NAME = "workflow_drafts"

        fun normalizeServer(value: String): String = value.trim().trimEnd('/').lowercase()

        fun identity(serverUrl: String, workflowPath: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("${normalizeServer(serverUrl)}\n$workflowPath".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
