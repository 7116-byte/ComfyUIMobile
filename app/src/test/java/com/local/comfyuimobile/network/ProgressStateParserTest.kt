package com.local.comfyuimobile.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressStateParserTest {
    @Test fun readsComfyUi028RunningDisplayNodeAndProgress() {
        val data = JSONObject(
            """{"prompt_id":"job-1","nodes":{"158":{"value":7,"max":20,"state":"running","node_id":"158","display_node_id":"158","real_node_id":"158"},"137":{"value":1,"max":1,"state":"finished"}}}""",
        )

        val update = ProgressStateParser.parse(data)!!

        assertEquals("job-1", update.promptId)
        assertEquals("158", update.nodeId)
        assertEquals(0.35f, update.progress)
    }

    @Test fun ignoresStateWithoutRunningNode() {
        assertNull(ProgressStateParser.parse(JSONObject("""{"prompt_id":"job-1","nodes":{"1":{"state":"pending"}}}""")))
    }

    @Test fun jsonNullDisplayNodeFallsBackToRealNode() {
        val data = JSONObject(
            """{"prompt_id":"job-2","nodes":{"158":{"value":1,"max":4,"state":"running","display_node_id":null,"real_node_id":"305"}}}""",
        )

        val update = ProgressStateParser.parse(data)!!

        assertEquals("305", update.nodeId)
        assertEquals(0.25f, update.progress)
    }
}
