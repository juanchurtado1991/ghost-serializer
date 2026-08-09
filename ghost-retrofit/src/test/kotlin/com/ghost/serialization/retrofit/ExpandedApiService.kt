package com.ghost.serialization.retrofit

import retrofit2.http.GET

interface ExpandedApiService {
    @GET("/empty")
    suspend fun getEmpty(): retrofit2.Response<RetrofitUser?>

    @GET("/no_content")
    suspend fun getNoContent(): Unit

    @com.ghost.serialization.annotations.GhostStrict
    @GET("/strict")
    suspend fun getStrictUser(): RetrofitUser

    @com.ghost.serialization.annotations.GhostCoerce
    @GET("/coerce")
    suspend fun getCoercedUser(): RetrofitUser

    @GET("/lenient")
    suspend fun getLenientUser(): RetrofitUser
}
