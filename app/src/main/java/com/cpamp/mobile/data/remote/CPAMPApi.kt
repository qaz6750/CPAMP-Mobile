package com.cpamp.mobile.data.remote

import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.ApiCallResponseDto
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.LogsDto
import com.cpamp.mobile.data.remote.model.ManagerInfoDto
import com.cpamp.mobile.data.remote.model.ManagerStatusDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
    suspend fun authFiles(): JsonElement

    @POST("v0/management/api-call")
    suspend fun apiCall(@Body request: ApiCallRequestDto): ApiCallResponseDto

    @GET("v0/management/logs")
    suspend fun logs(
        @Query("after") after: Long? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 300,
    ): LogsDto

    @DELETE("v0/management/logs")
    suspend fun clearLogs(): JsonElement

}
