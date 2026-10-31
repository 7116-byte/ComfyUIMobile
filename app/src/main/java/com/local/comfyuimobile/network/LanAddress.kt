package com.local.comfyuimobile.network

import java.net.URI
import java.util.Locale

object LanAddress {
    fun normalize(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "请输入 ComfyUI 地址" }
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "请使用 HTTP 或 HTTPS 的 ComfyUI 实例地址" }
        require(uri.rawUserInfo == null && uri.rawQuery == null) { "当前不支持账号或令牌链接，请使用无需平台登录的 ComfyUI 实例根地址；连接页可查看使用指南" }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "当前不支持带路径前缀的代理，请填写 ComfyUI 实例根地址；连接页可查看使用指南" }
        val host = uri.host ?: throw IllegalArgumentException("地址格式不正确")
        val port = if (uri.port == -1) { if (scheme == "https") 443 else 8188 } else uri.port
        require(port in 1..65535) { "端口无效" }
        val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val portSuffix = if (scheme == "https" && port == 443) "" else ":$port"
        return "$scheme://${formattedHost.lowercase(Locale.ROOT)}$portSuffix"
    }

    fun serverHost(address: String): String = URI(address).host

    fun webSocketUrl(address: String, clientId: String): String {
        val base = URI(normalize(address))
        val scheme = if (base.scheme == "https") "wss" else "ws"
        val query = "clientId=" + java.net.URLEncoder.encode(clientId, "UTF-8")
        return "$scheme://${base.rawAuthority}/ws?$query"
    }

    /** Compare parsed origins, never URL string prefixes (e.g. host.example.evil). */
    fun sameOrigin(target: String, server: String): Boolean = runCatching {
        val first = URI(target)
        val second = URI(server)
        fun port(uri: URI) = if (uri.port != -1) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        first.rawUserInfo == null && second.rawUserInfo == null &&
            first.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
            first.scheme.equals(second.scheme, true) &&
            first.host != null && first.host.equals(second.host, true) && port(first) == port(second)
    }.getOrDefault(false)

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
