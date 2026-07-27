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
}