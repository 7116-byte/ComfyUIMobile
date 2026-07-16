package com.local.comfyuimobile.network

import java.net.URLEncoder

object UserdataPath {
    fun encode(path: String): String = URLEncoder
        .encode(path.replace('\\', '/').trim('/'), Charsets.UTF_8.name())
        .replace("+", "%20")
        .replace("%7E", "~", ignoreCase = true)
}
