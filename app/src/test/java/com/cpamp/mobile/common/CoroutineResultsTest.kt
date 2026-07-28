package com.cpamp.mobile.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoroutineResultsTest {
    @Test
    fun `captures ordinary failures`() = runBlocking {
        val result = runSuspendCatching<Int> { error("failed") }

        assertEquals("failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `rethrows cancellation`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runSuspendCatching<Unit> { throw CancellationException("cancelled") }
            }
        }
    }
}
