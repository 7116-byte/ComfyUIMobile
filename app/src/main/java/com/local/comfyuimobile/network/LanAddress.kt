package com.local.comfyuimobile.network

import java.net.URI

object LanAddress {
    fun normalize(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "请输入 ComfyUI 地址" }
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        require(uri.scheme.equals("http", ignoreCase = true)) { "当前只支持 HTTP 地址" }
        val host = uri.host ?: throw IllegalArgumentException("地址格式不正确")
        val port = if (uri.port == -1) 8188 else uri.port
        require(port in 1..65535) { "端口无效" }
        val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        return "http://$formattedHost:$port"
    }

    fun isTrustedHost(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return false
        return runCatching { URI("http://$trimmed").host != null }.getOrDefault(false)
    }

    fun subnet24(address: String): List<String> {
        val parts = address.split('.')
        require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 })
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }
    }
}
