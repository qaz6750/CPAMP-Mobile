package com.cpamp.mobile.data.remote

import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class SessionApiClientFactoryTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: SessionApiClientFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        factory = SessionApiClientFactory(Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        factory.invalidate()
        server.shutdown()
    }

    @Test
    fun `injects bearer authorization without changing request body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        factory.api(session()).replaceApiKeys(listOf("client-secret"))

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer admin-secret", request.getHeader("Authorization"))
        assertEquals("[\"client-secret\"]", request.body.readUtf8())
    }

    @Test
    fun `retries a retryable get response once`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        factory.api(session()).status()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not replay a write response`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        assertThrows(HttpException::class.java) {
            runBlocking { factory.api(session()).replaceApiKeys(listOf("client-secret")) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `switching profiles creates a client with the new credential`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        factory.api(session(profileId = "one", key = "first-secret")).status()
        factory.api(session(profileId = "two", key = "second-secret")).status()

        assertEquals("Bearer first-secret", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer second-secret", server.takeRequest().getHeader("Authorization"))
    }

    private fun session(
        profileId: String = "profile",
        key: String = "admin-secret",
    ) = AuthenticatedSession(
        profile = ServerProfile(
            id = profileId,
            name = profileId,
            baseUrl = server.url("/").toString(),
            lastConnectedAt = 0,
        ),
        adminKey = key,
        service = "cpamp-manager",
    )
}