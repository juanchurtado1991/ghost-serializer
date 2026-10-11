package com.ghost.serialization.retrofit

import com.ghost.serialization.annotations.GhostCoerce
import com.ghost.serialization.annotations.GhostStrict
import retrofit2.Response
import retrofit2.http.GET

interface EndpointOptionsApiService {
    @GET("/empty")
    suspend fun getEmpty(): Response<RetrofitUser?>

    @GET("/no_content")
    suspend fun getNoContent(): Unit

    @GhostStrict
    @GET("/strict")
    suspend fun getStrictUser(): RetrofitUser

    @GhostCoerce
    @GET("/coerce")
    suspend fun getCoercedUser(): RetrofitUser

    @GET("/lenient")
    suspend fun getLenientUser(): RetrofitUser
}
