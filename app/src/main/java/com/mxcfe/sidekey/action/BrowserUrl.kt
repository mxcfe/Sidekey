package com.mxcfe.sidekey.action

import android.net.Uri

object BrowserUrl {
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = Uri.parse(withScheme)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (host.isBlank()) return null
        if (host != "localhost" && '.' !in host && ':' !in host) return null
        return uri.toString()
    }
}
