@file:OptIn(InternalGhostApi::class)

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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

class GhostRetrofitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MockApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        Ghost.addRegistry(RetrofitTestRegistry)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostConverterFactory.create())
            .build()

        apiService = retrofit.create(MockApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `deserializes simple object correctly with zero-copy okio stream`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": 42, "name": "John Doe", "isActive": true}""")
                .addHeader("Content-Type", "application/json")
        )

        val user = apiService.getUser()
        assertEquals(42, user.id)
        assertEquals("John Doe", user.name)
        assertTrue(user.isActive)

        val request = mockWebServer.takeRequest()
        assertEquals("/user", request.path)
    }

    @Test
    fun `resolves complex List generic via Retrofit ParameterizedType`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id": 1, "name": "Alice", "isActive": true}, {"id": 2, "name": "Bob", "isActive": false}]""")
        )

        val users = apiService.getUsers()
        assertEquals(2, users.size)
        assertEquals("Alice", users[0].name)
        assertEquals("Bob", users[1].name)
    }

    @Test
    fun `resolves complex Map generic via Retrofit ParameterizedType`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"total": 100, "page": 1}""")
        )

        val metadata = apiService.getMetadata()
        assertEquals(100, metadata["total"])
        assertEquals(1, metadata["page"])
    }

    @Test
    fun `resolves primitive return type correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("999")
        )

        val value = apiService.getPrimitive()
        assertEquals(999, value)
    }

    @Test
    fun `serializes request body correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": 7, "name": "Eve", "isActive": true}""")
        )

        val newUser = RetrofitUser(7, "Eve", true)
        val response = apiService.createUser(newUser)

        assertEquals("Eve", response.name)

        val request = mockWebServer.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("""{"id":7,"name":"Eve","isActive":true}""", requestBody)
    }

    @Test
    fun `throws robust exception on malformed json body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": 42, "name": "John Doe", "isActive": """) // Incomplete JSON
        )

        assertFailsWith<Exception> {
            apiService.getUser()
        }
    }

    @Test
    fun `handles HTML 500 error body gracefully instead of silent failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("<html><body>Internal Server Error</body></html>")
                .addHeader("Content-Type", "text/html")
        )

        assertFailsWith<retrofit2.HttpException> {
            apiService.getUser()
        }
    }
}
