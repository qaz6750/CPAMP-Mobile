package com.cpamp.mobile.data.auth

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ConnectionProbe(
    val service: String,
    val serverVersion: String?,
)

class ConnectionException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason {
        Unauthorized,
        UnsupportedLightMode,
        NotManagerServer,
        RedirectBlocked,
        Tls,
        Timeout,
        Network,
        Server,
        InvalidResponse,
    }
}

@Singleton
class ConnectionTester @Inject constructor(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun test(baseUrl: String, adminKey: String): ConnectionProbe = withContext(Dispatchers.IO) {
        require(adminKey.isNotBlank()) { "ADMIN_KEY_REQUIRED" }
        try {
            val request = authenticatedRequest("$baseUrl/status", adminKey)
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw ConnectionException(
                        ConnectionException.Reason.Unauthorized,
                        "ADMIN_KEY_REJECTED",
                    )
                    response.code in 300..399 -> throw ConnectionException(
                        ConnectionException.Reason.RedirectBlocked,
                        "SERVER_REDIRECT_BLOCKED",
                    )
                    response.code == 404 -> detectLightMode(baseUrl, adminKey)
                    !response.isSuccessful -> throw ConnectionException(
                        ConnectionException.Reason.Server,
                        "SERVER_ERROR_${response.code}",
                    )
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                            throw ConnectionException(
                                ConnectionException.Reason.InvalidResponse,
                                "INVALID_MANAGER_RESPONSE",
                            )
                        }
                        val service = root["service"]?.jsonPrimitive?.content.orEmpty()
                        if (!service.contains("manager", ignoreCase = true)) {
                            throw ConnectionException(
                                ConnectionException.Reason.NotManagerServer,
                                "NOT_CPAMP_MANAGER",
                            )
                        }
                        ConnectionProbe(
                            service = service,
                            serverVersion = response.header("x-cpa-version")
                                ?: response.header("x-cpamp-version")
                                ?: response.header("x-server-version"),
                        )
                    }
                }
            }
        } catch (error: ConnectionException) {
            throw error
        } catch (error: SSLException) {
            throw ConnectionException(ConnectionException.Reason.Tls, "TLS_VALIDATION_FAILED", error)
        } catch (error: SocketTimeoutException) {
            throw ConnectionException(ConnectionException.Reason.Timeout, "CONNECTION_TIMEOUT", error)
        } catch (error: IOException) {
            throw ConnectionException(ConnectionException.Reason.Network, "CONNECTION_FAILED", error)
        }
    }

    private fun detectLightMode(baseUrl: String, adminKey: String): Nothing {
        val request = authenticatedRequest("$baseUrl/v0/management/config", adminKey)
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 401 || response.code == 403) {
                throw ConnectionException(
                    ConnectionException.Reason.UnsupportedLightMode,
                    "LIGHT_MODE_UNSUPPORTED",
                )
            }
        }
        throw ConnectionException(ConnectionException.Reason.NotManagerServer, "NOT_CPAMP_MANAGER")
    }

    private fun authenticatedRequest(url: String, adminKey: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $adminKey")
        .header("Accept", "application/json")
        .get()
        .build()
}

