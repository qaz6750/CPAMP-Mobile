package com.cpamp.mobile.ui.common

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun Long.compactNumber(): String {
    val value = this.toDouble()
    return when {
        this >= 1_000_000_000 -> formatCompact(value / 1_000_000_000, "B")
        this >= 1_000_000 -> formatCompact(value / 1_000_000, "M")
        this >= 1_000 -> formatCompact(value / 1_000, "K")
        else -> NumberFormat.getIntegerInstance().format(this)
    }
}

private fun formatCompact(value: Double, suffix: String): String =
    if (value >= 100 || value % 1.0 == 0.0) "%.0f%s".format(Locale.US, value, suffix)
    else "%.1f%s".format(Locale.US, value, suffix)

fun Long.compactTokens(): String {
    val value = this.toDouble()
    return when {
        this >= 1_000_000_000 -> formatTokens(value / 1_000_000_000, "B")
        this >= 1_000_000 -> formatTokens(value / 1_000_000, "M")
        this >= 10_000 -> formatTokens(value / 1_000, "K")
        else -> NumberFormat.getIntegerInstance().format(this)
    }
}

private fun formatTokens(value: Double, suffix: String): String =
    if (value >= 100) "%.0f%s".format(Locale.US, value, suffix)
    else "%.2f%s".format(Locale.US, value, suffix).replace(Regex("\\.?0+(?=[A-Z]$)"), "")

fun Double.asPercent(): String = "%.1f%%".format(Locale.US, this * 100.0)

fun Double.asCost(): String = when {
    this == 0.0 -> "\$0.00"
    this < 0.01 -> "\$%.4f".format(Locale.US, this)
    else -> "\$%.2f".format(Locale.US, this)
}

fun Double.asLatency(): String = when {
    this < 1_000 -> "%.0f ms".format(Locale.US, this)
    this < 10_000 -> "%.2f s".format(Locale.US, this / 1_000)
    this < 100_000 -> "%.1f s".format(Locale.US, this / 1_000)
    else -> "%.0f s".format(Locale.US, this / 1_000)
}

fun Long.asLatency(): String = toDouble().asLatency()

fun Long.asTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

fun Long.asDateTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault()),
    )
