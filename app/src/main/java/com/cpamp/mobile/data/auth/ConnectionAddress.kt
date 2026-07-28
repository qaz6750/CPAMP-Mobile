package com.cpamp.mobile.data.auth

import java.net.URI

object ConnectionAddress {
    fun normalize(raw: String): String {
        var candidate = raw.trim()
        require(candidate.isNotBlank()) { "SERVER_ADDRESS_REQUIRED" }
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
        require(uri.port == -1 || uri.port in 1..65535) { "SERVER_ADDRESS_INVALID" }
        val path = uri.rawPath.orEmpty()
        require(ALLOWED_PATHS.any { it.equals(path, ignoreCase = true) }) { "SERVER_PATH_UNSUPPORTED" }
        val normalizedScheme = uri.scheme.lowercase()
        val host = uri.host.lowercase()
        val normalizedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$normalizedScheme://$normalizedHost$port"
    }

    fun defaultLabel(normalized: String): String {
        val uri = URI(normalized)
        return if (uri.port >= 0) "${uri.host}:${uri.port}" else uri.host
    }

    private val ALLOWED_PATHS = setOf(
        "",
        "/",
        "/v0/management",
        "/v0/management/",
        "/management.html",
        "/management.html/",
    )
}
