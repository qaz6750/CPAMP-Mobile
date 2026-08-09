package com.cpamp.mobile.data.remote

import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.ApiCallResponseDto
import com.cpamp.mobile.data.remote.model.AuthFilesResponseDto
import com.cpamp.mobile.data.remote.model.CodexInspectionRunDetailDto
import com.cpamp.mobile.data.remote.model.CodexInspectionRunsResponseDto
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CPAMPApi {
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
    suspend fun authFiles(): AuthFilesResponseDto

    @POST("v0/management/api-call")
    suspend fun apiCall(@Body request: ApiCallRequestDto): ApiCallResponseDto

    @GET("v0/management/codex-inspection/runs")
    suspend fun codexInspectionRuns(@Query("limit") limit: Int = 20): CodexInspectionRunsResponseDto

    @GET("v0/management/codex-inspection/runs/{id}")
    suspend fun codexInspectionRun(@Path("id") id: Long): CodexInspectionRunDetailDto

}
