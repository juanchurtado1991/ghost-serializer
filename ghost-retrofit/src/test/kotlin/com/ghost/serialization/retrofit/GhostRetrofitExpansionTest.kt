package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
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

    @com.ghost.serialization.annotations.GhostStrict
    @GET("/strict")
    suspend fun getStrictUser(): RetrofitUser

    @com.ghost.serialization.annotations.GhostCoerce
    @GET("/coerce")
    suspend fun getCoercedUser(): RetrofitUser

    @GET("/lenient")
    suspend fun getLenientUser(): RetrofitUser
}

@OptIn(InternalGhostApi::class)
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

    @Test
    fun `strict endpoint throws on missing comma`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":1 \"name\":\"John\"}"))
        kotlin.test.assertFailsWith<Exception> {
            apiService.getStrictUser()
        }
    }

    @Test
    fun `lenient endpoint passes on missing comma`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":1 \"name\":\"John\"}"))
        val user = apiService.getLenientUser()
        assertEquals(1, user.id)
        assertEquals("John", user.name)
    }

    @Test
    fun `coerced endpoint coerces primitive values`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"42\", \"name\":\"John\", \"isActive\":\"true\"}"))
        val user = apiService.getCoercedUser()
        assertEquals(42, user.id)
        assertEquals("John", user.name)
        assertEquals(true, user.isActive)
    }
}
