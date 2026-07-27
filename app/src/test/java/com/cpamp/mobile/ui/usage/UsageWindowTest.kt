package com.cpamp.mobile.ui.usage

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageWindowTest {
    @Test
    fun `today starts at local midnight`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 27, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(UsageRange(expected, now), usageWindowRange(UsageWindow.Day, now, zone))
    }

    @Test
    fun `rolling ranges retain exact duration`() {
        val now = 2_000_000_000_000L
        assertEquals(UsageWindow.Week.durationMs, now - usageWindowRange(UsageWindow.Week, now).fromMs)
        assertEquals(UsageWindow.Month.durationMs, now - usageWindowRange(UsageWindow.Month, now).fromMs)
    }
}