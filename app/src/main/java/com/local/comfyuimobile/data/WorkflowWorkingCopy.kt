package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.WorkflowDocument

object WorkflowWorkingCopy {
    fun frontendPath(document: WorkflowDocument): String? =
        document.entry.path.takeIf { document.sourceJobId == null && !document.isTemporary }

    suspend fun serialize(document: WorkflowDocument, load: suspend (WorkflowDocument) -> Unit,
                          sync: suspend (List<ParameterField>) -> String): String {
        load(document)
        return sync(document.fields)
    }

    fun same(a: WorkflowDocument?, b: WorkflowDocument?): Boolean =
        a != null && b != null && a.serverUrl == b.serverUrl && a.entry.path == b.entry.path

    fun preview(preview: WorkflowDocument?, selected: WorkflowDocument?, fields: List<ParameterField>): WorkflowDocument? =
        if (same(preview, selected)) selected?.copy(fields = fields) else preview
}
