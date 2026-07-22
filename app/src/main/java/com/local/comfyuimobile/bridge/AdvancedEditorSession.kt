package com.local.comfyuimobile.bridge

object AdvancedEditorSession {
    @Volatile private var inputWorkflow: String? = null
    @Volatile private var outputWorkflow: String? = null

    @Synchronized
    fun begin(workflowJson: String) {
        inputWorkflow = workflowJson
        outputWorkflow = null
    }

    fun input(): String? = inputWorkflow

    @Synchronized
    fun complete(workflowJson: String) {
        outputWorkflow = workflowJson
    }

    @Synchronized
    fun consumeOutput(): String? = outputWorkflow.also {
        inputWorkflow = null
        outputWorkflow = null
    }

    @Synchronized
    fun clear() {
        inputWorkflow = null
        outputWorkflow = null
    }
}
