package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.WorkflowNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExecutionNodeResolverTest {
    private val nodes = listOf(
        WorkflowNode(id = "68", type = "Subgraph", title = "放大", order = 0),
        WorkflowNode(id = "74", type = "Image Comparer (rgthree)", title = "图片对比", order = 1),
    )

    @Test fun mapsNativeSubgraphChildToVisibleOuterNode() {
        assertEquals("68", ExecutionNodeResolver.resolve("68:76", nodes))
    }

    @Test fun keepsDirectVisibleNode() {
        assertEquals("74", ExecutionNodeResolver.resolve("74", nodes))
    }

    @Test fun keepsUnknownRuntimeNodeForDiagnostics() {
        assertEquals("999", ExecutionNodeResolver.resolve("999", nodes))
    }

    @Test fun returnsNullForMissingRuntimeNode() {
        assertNull(ExecutionNodeResolver.resolve(null, nodes))
    }
}
