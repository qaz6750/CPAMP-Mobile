package com.cpamp.mobile.ui.common

object SensitiveText {
    private val bearer = Regex("""(?i)bearer\s+[a-z0-9._~+/-]+=*""")
    private val knownKey = Regex("""(?i)\b(?:sk|cpamp|key|token)[-_][a-z0-9_-]{8,}""")
    private val jsonSecret = Regex("""(?i)(["']?(?:api[_-]?key|authorization|token|secret)["']?\s*[:=]\s*["']?)[^"'\s,}]+""")

    fun redact(value: String, maxLength: Int = 320): String {
        if (value.isBlank()) return value
        return value
            .take(maxLength)
            .replace(bearer, "Bearer ••••")
            .replace(knownKey, "••••")
            .replace(jsonSecret, "\$1••••")
    }
}
