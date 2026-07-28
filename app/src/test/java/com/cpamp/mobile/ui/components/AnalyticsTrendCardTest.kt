package com.cpamp.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsTrendCardTest {
    @Test
    fun `maps chart positions to nearest point`() {
        assertEquals(0, trendPointIndex(0f, 100f, 3))
        assertEquals(1, trendPointIndex(50f, 100f, 3))
        assertEquals(2, trendPointIndex(100f, 100f, 3))
        assertEquals(0, trendPointIndex(80f, 100f, 1))
        assertNull(trendPointIndex(20f, 100f, 0))
    }

    @Test
    fun `resolves explicit next and inferred bucket ends`() {
        val points = listOf(
            AnalyticsTrendPoint(1_000, 1, 1),
            AnalyticsTrendPoint(2_000, 1, 1),
            AnalyticsTrendPoint(3_000, 1, 1, bucketEndMs = 5_000),
        )

        assertEquals(2_000, trendBucketEnd(points, 0))
        assertEquals(3_000, trendBucketEnd(points, 1))
        assertEquals(5_000, trendBucketEnd(points, 2))
    }

    @Test
    fun `dashboard removes leading empty and future buckets`() {
        val hour = 60 * 60 * 1000L
        val points = listOf(
            AnalyticsTrendPoint(0, 0, 0),
            AnalyticsTrendPoint(hour, 2, 20),
            AnalyticsTrendPoint(hour * 2, 0, 0),
            AnalyticsTrendPoint(hour * 3, 4, 40),
        )

        assertEquals(points.subList(1, 3), dashboardVisibleTrafficPoints(points, hour * 2 + 1))
        assertEquals(true, isCurrentTrafficBucket(points[2], hour * 2 + 1))
    }
}
