package com.cpamp.mobile.ui.common

import java.net.URI

fun safeServerName(
    name: String,
    baseUrl: String,
    hideAddresses: Boolean,
    fallback: String,
): String {
    val candidate = name.trim()
    if (!hideAddresses) return candidate.ifBlank { fallback }
    if (candidate.isBlank() || candidate.looksLikeAddress()) return fallback

    val host = runCatching { URI(baseUrl).host }.getOrNull()
    return if (host != null && candidate.equals(host, ignoreCase = true)) fallback else candidate
}

internal fun String.looksLikeAddress(): Boolean {
    val candidate = trim()
    if (candidate.contains("://") || candidate.equals("localhost", ignoreCase = true)) return true
    if (candidate.startsWith("[") && candidate.contains(']')) return true
    if (candidate.count { it == ':' } >= 2) return true
    if (HOST_PORT.matches(candidate) || HOST_NAME.matches(candidate)) return true
    return IPV4.matches(candidate) && candidate.split('.').all { part -> part.toIntOrNull() in 0..255 }
}

private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
private val HOST_PORT = Regex("^[^\\s/:]+:\\d{1,5}$")
private val HOST_NAME = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]*\\.)+[A-Za-z]{2,63}$")