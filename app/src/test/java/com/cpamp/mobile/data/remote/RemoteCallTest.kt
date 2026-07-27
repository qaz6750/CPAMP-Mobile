package com.cpamp.mobile.data.remote

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RemoteCallTest {
    @Test
    fun `maps authentication and rate limit responses`() {
        listOf(
            401 to RemoteFailure.Unauthorized::class.java,
            403 to RemoteFailure.Unauthorized::class.java,
            429 to RemoteFailure.RateLimited::class.java,
        ).forEach { (status, expected) ->
            assertThrows(expected) {
                runBlocking { remoteCall<Unit> { throw httpException(status) } }
            }
        }
    }

    @Test
    fun `maps timeout without exposing its message`() {
        val failure = assertThrows(RemoteFailure.Timeout::class.java) {
            runBlocking { remoteCall<Unit> { throw SocketTimeoutException("secret upstream detail") } }
        }
        assertEquals("REMOTE_TIMEOUT", failure.message)
    }

    private fun httpException(status: Int): HttpException =
        HttpException(Response.error<Unit>(status, okhttp3.ResponseBody.create(null, ByteArray(0))))
}