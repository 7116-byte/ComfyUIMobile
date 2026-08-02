package com.local.comfyuimobile.bridge

object AdvancedEditorSession {
    @Volatile private var inputWorkflow: String? = null
    @Volatile private var inputWorkflowPath: String? = null
    @Volatile private var outputWorkflow: String? = null

    @Synchronized
    fun begin(workflowJson: String, workflowPath: String) {
        inputWorkflow = workflowJson
        inputWorkflowPath = workflowPath
        outputWorkflow = null
    }

    fun input(): String? = inputWorkflow
    fun inputPath(): String? = inputWorkflowPath

    @Synchronized
    fun complete(workflowJson: String) {
        outputWorkflow = workflowJson
    }

    @Synchronized
    fun consumeOutput(): String? = outputWorkflow.also {
        inputWorkflow = null
        inputWorkflowPath = null
        outputWorkflow = null
    }

    @Synchronized
    fun clear() {
        inputWorkflow = null
        inputWorkflowPath = null
        outputWorkflow = null
    }
}
