package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.http.GET
import kotlin.test.assertEquals
import kotlin.test.assertNull

interface ExpandedApiService {
    @GET("/empty")
    suspend fun getEmpty(): retrofit2.Response<RetrofitUser?>

    @GET("/no_content")
    suspend fun getNoContent(): Unit
}

class GhostRetrofitExpansionTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ExpandedApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        Ghost.addRegistry(RetrofitTestRegistry)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostConverterFactory.create())
            .build()

        apiService = retrofit.create(ExpandedApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `handles 204 No Content correctly`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))
        val result = apiService.getNoContent()
        assertEquals(Unit, result)
    }

    @Test
    fun `handles null response body correctly`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("null"))
        val response = apiService.getEmpty()
        assertNull(response.body())
    }
}
