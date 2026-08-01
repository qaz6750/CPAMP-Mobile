package com.cpamp.mobile.ui.usage

import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.data.remote.model.MonitoringTimelineDto
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `week range retains exact duration`() {
        val now = 2_000_000_000_000L
        assertEquals(UsageWindow.Week.durationMs, now - usageWindowRange(UsageWindow.Week, now).fromMs)
    }

    @Test
    fun `month contains today and previous 29 local dates`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 3, 30, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(UsageRange(expected, now), usageWindowRange(UsageWindow.Month, now, zone))
    }

    @Test
    fun `specific historical month covers the complete local month`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 27, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expectedFrom = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedTo = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli() - 1

        assertEquals(
            UsageRange(expectedFrom, expectedTo),
            usageWindowRange(UsageWindow.SpecificMonth, now, zone, YearMonth.of(2026, 2)),
        )
    }

    @Test
    fun `specific current month does not extend beyond now`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 27, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expectedFrom = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            UsageRange(expectedFrom, now),
            usageWindowRange(UsageWindow.SpecificMonth, now, zone, YearMonth.of(2026, 7)),
        )
    }

    @Test
    fun `available months include only timeline buckets with usage`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val candidates = setOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7))
        val timeline = listOf(
            MonitoringTimelineDto(
                bucketMs = ZonedDateTime.of(2026, 7, 3, 0, 0, 0, 0, zone).toInstant().toEpochMilli(),
                calls = 2,
            ),
            MonitoringTimelineDto(
                bucketMs = ZonedDateTime.of(2026, 6, 15, 0, 0, 0, 0, zone).toInstant().toEpochMilli(),
                totalTokens = 30,
            ),
            MonitoringTimelineDto(
                bucketMs = ZonedDateTime.of(2026, 5, 20, 0, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ),
        )

        assertEquals(
            listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 6)),
            availableUsageMonths(timeline, zone, candidates),
        )
    }

    @Test
    fun `effective range starts on first active local date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val firstActive = ZonedDateTime.of(2026, 3, 19, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 3, 30, 15, 30, 0, 0, zone).toInstant().toEpochMilli()
        val response = MonitoringResponseDto(
            timeline = listOf(
                MonitoringTimelineDto(bucketMs = start, calls = 0),
                MonitoringTimelineDto(bucketMs = firstActive, calls = 3),
            ),
        )

        assertEquals(
            UsageEffectiveRange(
                fromMs = ZonedDateTime.of(2026, 3, 19, 0, 0, 0, 0, zone).toInstant().toEpochMilli(),
                toMs = now,
                actualDays = 12,
            ),
            effectiveUsageRange(response, UsageRange(start, now), zone),
        )
    }

    @Test
    fun `effective range is absent when all buckets are empty`() {
        val range = UsageRange(1_000, 2_000)
        val response = MonitoringResponseDto(
            timeline = listOf(MonitoringTimelineDto(bucketMs = 1_000, calls = 0, tokens = 0)),
        )

        assertNull(effectiveUsageRange(response, range, ZoneId.of("UTC")))
    }
}
