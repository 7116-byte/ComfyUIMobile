package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.WorkflowManifest

data class AdvancedEditorResult(
    val workflowJson: String,
    val manifest: WorkflowManifest,
)

object AdvancedEditorSession {
    @Volatile private var inputWorkflow: String? = null
    @Volatile private var inputWorkflowPath: String? = null
    @Volatile private var output: AdvancedEditorResult? = null

    @Synchronized
    fun begin(workflowJson: String, workflowPath: String) {
        inputWorkflow = workflowJson
        inputWorkflowPath = workflowPath
        output = null
    }

    fun input(): String? = inputWorkflow
    fun inputPath(): String? = inputWorkflowPath

    @Synchronized
    fun complete(workflowJson: String, manifest: WorkflowManifest) {
        output = AdvancedEditorResult(workflowJson, manifest)
    }

    @Synchronized
    fun consumeOutput(): AdvancedEditorResult? = output.also {
        inputWorkflow = null
        inputWorkflowPath = null
        output = null
    }

    @Synchronized
    fun clear() {
        inputWorkflow = null
        inputWorkflowPath = null
        output = null
    }
}
