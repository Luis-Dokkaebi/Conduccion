package com.dms.app

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface DmsApi {
    @POST("/api/v1/telemetry/events")
    suspend fun syncEvents(@Body events: List<MicroSleepEvent>): Response<Void>
}
