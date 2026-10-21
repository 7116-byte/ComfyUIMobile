package com.local.comfyuimobile.network

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComfyClientPromptMetadataTest {
    @Test fun submissionKeepsTheEntireEditorWorkflowAndDoesNotSaveAWorkflowFile() = runBlocking {
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().buffered()
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val value = input.read()
                    check(value >= 0)
                    bytes.write(value)
                    if (bytes.size() >= 4 && bytes.toByteArray().takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) break
                }
                val headers = bytes.toString("UTF-8").split("\r\n")
                val size = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                val body = ByteArray(size)
                var offset = 0
                while (offset < size) {
                    val count = input.read(body, offset, size - offset)
                    check(count > 0)
                    offset += count
                }
                requests += headers.first().split(' ')[1] to body.toString(Charsets.UTF_8)
                val response = """{"prompt_id":"test-job","number":1}""".toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(response)
                    flush()
                }
            }
        }
        try {
            val client = ComfyClient().apply { setServer("http://127.0.0.1:${server.localPort}") }
            val workflow = """{"id":"c5391984-dd84-4e04-a7c2-18125c7e88d9","version":0.4,
                "nodes":[{"id":18,"type":"subgraph-id","widgets_values":[123456789012345]}],
                "links":[[1,18,0,9,0,"IMAGE"]],
                "definitions":{"subgraphs":[{"id":"subgraph-id","nodes":[{"id":7,"type":"KSampler","widgets_values":[123456789012345,"randomize"]}]}]},
                "extra":{"comfyMobile":{"schema":1,"fields":{}},"custom_extension":{"enabled":true}}}"""
            val prompt = """{"18:7":{"class_type":"KSampler","inputs":{"seed":123456789012345}}}"""
            val response = client.queuePrompt(prompt, workflow, "mobile-client", "workflows/example.json", "example.json")
            assertEquals("test-job", response.promptId)
            assertEquals(listOf("/prompt"), requests.map { it.first })
            val sent = JSONObject(requests.single().second)
            assertEquals("mobile-client", sent.getString("client_id"))
            val embedded = sent.getJSONObject("extra_data").getJSONObject("extra_pnginfo").getJSONObject("workflow")
            assertTrue(JSONObject(workflow).similar(embedded))
            assertTrue(JSONObject(prompt).similar(sent.getJSONObject("prompt")))
            assertEquals(123456789012345L, embedded.getJSONArray("nodes").getJSONObject(0).getJSONArray("widgets_values").getLong(0))
        } finally { server.close(); worker.join(1_000) }
    }
}
