package com.cpamp.mobile.data.auth

import java.net.URI

object ConnectionAddress {
    fun normalize(raw: String): String {
        var candidate = raw.trim()
        require(candidate.isNotBlank()) { "SERVER_ADDRESS_REQUIRED" }
        candidate = candidate.replace(Regex("""(?i)/?(?:v0/management|management\.html)/?$"""), "")
            .trimEnd('/')
        if (!candidate.startsWith("http://", true) && !candidate.startsWith("https://", true)) {
            candidate = "https://$candidate"
        }

        val uri = runCatching { URI(candidate) }.getOrElse { throw IllegalArgumentException("SERVER_ADDRESS_INVALID") }
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "SERVER_SCHEME_INVALID"
        }
        require(!uri.host.isNullOrBlank()) { "SERVER_ADDRESS_INVALID" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "SERVER_ADDRESS_INVALID"
        }
        val path = uri.path.orEmpty().trimEnd('/')
        require(path.isBlank()) { "SERVER_PATH_UNSUPPORTED" }
        val normalizedScheme = uri.scheme.lowercase()
        val normalizedHost = if (uri.host.contains(':') && !uri.host.startsWith('[')) "[${uri.host}]" else uri.host
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$normalizedScheme://$normalizedHost$port"
    }

    fun defaultLabel(normalized: String): String {
        val uri = URI(normalized)
        return if (uri.port >= 0) "${uri.host}:${uri.port}" else uri.host
    }
}
