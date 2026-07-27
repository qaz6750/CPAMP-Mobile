package com.cpamp.mobile.data.remote

import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

@Singleton
class SessionApiClientFactory @Inject constructor(
    private val json: Json,
) {
    @Volatile
    private var cached: CachedClient? = null

    fun api(session: AuthenticatedSession): CPAMPApi {
        val existing = cached
        if (existing?.matches(session) == true) return existing.api

        return synchronized(this) {
            cached?.takeIf { it.matches(session) }?.api ?: create(session).also { cached = it }.api
        }
    }

    fun invalidate() {
        synchronized(this) {
            cached?.client?.dispatcher?.cancelAll()
            cached?.client?.connectionPool?.evictAll()
            cached = null
        }
    }

    private fun create(session: AuthenticatedSession): CachedClient {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addInterceptor(AuthInterceptor(session.adminKey))
            .addInterceptor(IdempotentRetryInterceptor())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(session.profile.baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()

        return CachedClient(
            profileId = session.profile.id,
            baseUrl = session.profile.baseUrl,
            adminKey = session.adminKey,
            client = client,
            api = retrofit.create(CPAMPApi::class.java),
        )
    }

    private data class CachedClient(
        val profileId: String,
        val baseUrl: String,
        val adminKey: String,
        val client: OkHttpClient,
        val api: CPAMPApi,
    ) {
        fun matches(session: AuthenticatedSession): Boolean =
            profileId == session.profile.id &&
                baseUrl == session.profile.baseUrl &&
                adminKey == session.adminKey
    }

    private class AuthInterceptor(private val adminKey: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $adminKey")
                .header("Accept", "application/json")
                .header("User-Agent", "CPAMPMobile/${BuildConfig.VERSION_NAME} Android")
                .build()
            return chain.proceed(request)
        }
    }

    private class IdempotentRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val first = chain.proceed(request)
            if (request.method != "GET" || first.code !in RETRYABLE_CODES) return first
            first.close()
            return chain.proceed(request)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val RETRYABLE_CODES = setOf(502, 503, 504)
    }
}

