package com.dms.app

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class ClearanceResponse(
    val status: String,
    val frs_score: Float,
    val message: String,
    val mandatory_rest_minutes: Int
)

interface DmsApi {
    @POST("/api/v1/telemetry/events")
    suspend fun syncEvents(@Body events: List<MicroSleepEvent>): Response<Void>

    @GET("/api/v1/mobile_dms/clearance/{driver_id}")
    suspend fun getClearance(@Path("driver_id") driverId: String): Response<ClearanceResponse>

    @POST("/api/v1/mobile_dms/end_shift/{driver_id}")
    suspend fun endShift(@Path("driver_id") driverId: String): Response<Void>
}
