package com.local.comfyuimobile.model

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }

data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lastSeen: Long = 0L,
    val comfyVersion: String = "",
)

data class DeviceStats(
    val name: String,
    val vramTotal: Long,
    val vramFree: Long,
)

data class SystemStats(
    val comfyVersion: String,
    val frontendVersion: String,
    val devices: List<DeviceStats>,
)

data class WorkflowEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modified: Double = 0.0,
)

enum class ParameterKind { TEXT, MULTILINE, INTEGER, DECIMAL, BOOLEAN, COMBO, IMAGE, VIDEO, UNSUPPORTED }
enum class ParameterSection { PRIMARY, MORE }

data class ParameterField(
    val key: String,
    val nodeId: String,
    val nodeTitle: String,
    val nodeType: String,
    val name: String,
    val label: String,
    val widgetType: String,
    val kind: ParameterKind,
    val valueJson: String,
    val displayValue: String,
    val options: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val step: Double? = null,
    val linked: Boolean = false,
    val visible: Boolean = true,
    val section: ParameterSection = ParameterSection.MORE,
    val order: Int = 0,
    val warning: String? = null,
    val widgetIndex: Int = -1,
    val refreshesWorkflow: Boolean = false,
)

data class WorkflowDocument(
    val entry: WorkflowEntry,
    val rawJson: String,
    val fields: List<ParameterField>,
)

enum class JobState { RUNNING, PENDING, SUCCESS, ERROR, CANCELLED, UNKNOWN }

data class JobSummary(
    val id: String,
    val state: JobState,
    val workflowName: String = "",
    val progress: Float? = null,
    val currentNode: String? = null,
    val submittedByApp: Boolean = false,
    val message: String = "",
)

enum class MediaKind { IMAGE, VIDEO }

data class ResultMedia(
    val jobId: String,
    val nodeId: String,
    val filename: String,
    val subfolder: String,
    val type: String,
    val kind: MediaKind,
    val url: String,
)

data class GeneratedPrompt(
    val promptJson: String,
    val workflowJson: String,
)

data class UpdateInfo(
    val tag: String,
    val apkUrl: String,
    val sha256Url: String?,
    val releaseUrl: String,
)

data class AppUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "尚未连接",
    val connectionStep: Int = 0,
    val connectionTotalSteps: Int = 6,
    val serverInput: String = "http://192.168.10.109:8188",
    val activeServer: ServerProfile? = null,
    val savedServers: List<ServerProfile> = emptyList(),
    val discoveredServers: List<ServerProfile> = emptyList(),
    val systemStats: SystemStats? = null,
    val queueRemaining: Int = 0,
    val workflows: List<WorkflowEntry> = emptyList(),
    val selectedWorkflow: WorkflowDocument? = null,
    val fields: List<ParameterField> = emptyList(),
    val jobs: List<JobSummary> = emptyList(),
    val results: List<ResultMedia> = emptyList(),
    val promptHistory: List<String> = emptyList(),
    val submittedJobIds: Set<String> = emptySet(),
    val autoSaveResults: Boolean = false,
    val loading: Boolean = false,
    val scanning: Boolean = false,
    val generating: Boolean = false,
    val bridgeReady: Boolean = false,
    val advancedEditor: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val updateInfo: UpdateInfo? = null,
)
