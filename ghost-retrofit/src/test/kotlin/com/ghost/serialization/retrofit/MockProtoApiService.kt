@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MockProtoApiService {
    @GET("/event")
    suspend fun getEvent(): ProtoDeviceEvent

    @GET("/events")
    suspend fun getEvents(): List<ProtoDeviceEvent>

    @POST("/event")
    suspend fun createEvent(@Body event: ProtoDeviceEvent): ProtoDeviceEvent
}
