@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MockYamlApiService {
    @GET("/profile")
    suspend fun getProfile(): YamlDeviceProfile

    @POST("/profile")
    suspend fun createProfile(@Body profile: YamlDeviceProfile): YamlDeviceProfile
}
