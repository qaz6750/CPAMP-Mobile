package com.cpamp.mobile.data.remote

import com.cpamp.mobile.data.remote.model.ApiKeyPatchDto
import com.cpamp.mobile.data.remote.model.ApiKeysDto
import com.cpamp.mobile.data.remote.model.AuthFileDeleteResultDto
import com.cpamp.mobile.data.remote.model.AuthFileStatusPatchDto
import com.cpamp.mobile.data.remote.model.AuthFilesDto
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.LogsDto
import com.cpamp.mobile.data.remote.model.ManagerInfoDto
import com.cpamp.mobile.data.remote.model.ManagerStatusDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.data.remote.model.QuotaCooldownsDto
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

interface CPAMPApi {
    @GET("usage-service/info")
    suspend fun info(): ManagerInfoDto

    @GET("status")
    suspend fun status(): ManagerStatusDto

    @GET("v0/management/dashboard/summary")
    suspend fun dashboard(
        @Query("today_start_ms") todayStartMs: Long,
        @Query("now_ms") nowMs: Long,
        @Query("top_models") topModels: Int = 6,
        @Query("recent_failures") recentFailures: Int = 8,
    ): DashboardSummaryDto

    @POST("v0/management/monitoring/analytics")
    suspend fun monitoring(@Body request: MonitoringRequestDto): MonitoringResponseDto

    @GET("v0/management/auth-files")
    suspend fun authFiles(): AuthFilesDto

    @PATCH("v0/management/auth-files")
    suspend fun patchAuthFile(@Body patch: AuthFileStatusPatchDto): JsonElement

    @HTTP(method = "DELETE", path = "v0/management/auth-files", hasBody = false)
    suspend fun deleteAuthFile(@Query("name") name: String): AuthFileDeleteResultDto

    @Multipart
    @POST("v0/management/auth-files")
    suspend fun uploadAuthFile(@Part file: MultipartBody.Part): JsonElement

    @GET("v0/management/auth-files/download")
    suspend fun downloadAuthFile(@Query("name") name: String): ResponseBody

    @GET("v0/management/api-keys")
    suspend fun apiKeys(): ApiKeysDto

    @PUT("v0/management/api-keys")
    suspend fun replaceApiKeys(@Body keys: List<String>): JsonElement

    @PATCH("v0/management/api-keys")
    suspend fun updateApiKey(@Body patch: ApiKeyPatchDto): JsonElement

    @DELETE("v0/management/api-keys")
    suspend fun deleteApiKey(@Query("index") index: Int): JsonElement

    @GET("usage-service/quota-cooldowns")
    suspend fun quotaCooldowns(): QuotaCooldownsDto

    @GET("v0/management/logs")
    suspend fun logs(
        @Query("after") after: Long? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 300,
    ): LogsDto

    @DELETE("v0/management/logs")
    suspend fun clearLogs(): JsonElement

    @GET
    suspend fun getDynamic(@Url relativeUrl: String): JsonElement

    @PUT
    suspend fun putDynamic(@Url relativeUrl: String, @Body body: JsonElement): JsonElement

    @PUT
    suspend fun putDynamicUnit(@Url relativeUrl: String, @Body body: JsonElement)

    @PATCH
    suspend fun patchDynamic(@Url relativeUrl: String, @Body body: JsonElement): JsonElement

    @DELETE
    suspend fun deleteDynamic(@Url relativeUrl: String): JsonElement
}
