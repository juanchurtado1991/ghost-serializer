@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// --- Retrofit API Definition ---
interface MockApiService {
    @GET("/user")
    suspend fun getUser(): RetrofitUser

    @GET("/users")
    suspend fun getUsers(): List<RetrofitUser>

    @GET("/metadata")
    suspend fun getMetadata(): Map<String, Int>

    @POST("/user")
    suspend fun createUser(
        @Body user: RetrofitUser
    ): RetrofitUser

    @GET("/primitive")
    suspend fun getPrimitive(): Int
}
