package com.local.comfyuimobile.network

import java.net.URI

object LanAddress {
    fun normalize(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "请输入 ComfyUI 地址" }
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        require(uri.scheme.equals("http", ignoreCase = true)) { "首版只支持可信局域网 HTTP 地址" }
        val host = uri.host ?: throw IllegalArgumentException("地址格式不正确")
        require(isTrustedHost(host)) { "只允许私有局域网地址或本地主机名" }
        val port = if (uri.port == -1) 8188 else uri.port
        require(port in 1..65535) { "端口无效" }
        return "http://$host:$port"
    }

    fun isTrustedHost(host: String): Boolean {
        val lowered = host.lowercase()
        if (lowered == "localhost" || lowered.endsWith(".local")) return true
        val parts = lowered.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            parts[0] == 127
    }

    fun subnet24(address: String): List<String> {
        val parts = address.split('.')
        require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 })
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }
    }
}
