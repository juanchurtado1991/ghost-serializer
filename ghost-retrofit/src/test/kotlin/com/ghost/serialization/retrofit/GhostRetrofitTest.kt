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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostRetrofitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MockApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        Ghost.addRegistry(registry = RetrofitTestRegistry)

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
                .addHeader(GhostRetrofitMediaTypes.CONTENT_TYPE_HEADER, GhostRetrofitMediaTypes.APPLICATION_JSON)
        )

        val user = apiService.getUser()
        assertEquals(expected = 42, actual = user.id)
        assertEquals(expected = "John Doe", actual = user.name)
        assertTrue(actual = user.isActive)

        val request = mockWebServer.takeRequest()
        assertEquals(expected = "/user", actual = request.path)
    }

    @Test
    fun `resolves complex List generic via Retrofit ParameterizedType`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id": 1, "name": "Alice", "isActive": true}, {"id": 2, "name": "Bob", "isActive": false}]""")
        )

        val users = apiService.getUsers()
        assertEquals(expected = 2, actual = users.size)
        assertEquals(expected = "Alice", actual = users[0].name)
        assertEquals(expected = "Bob", actual = users[1].name)
    }

    @Test
    fun `resolves complex Map generic via Retrofit ParameterizedType`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"total": 100, "page": 1}""")
        )

        val metadata = apiService.getMetadata()
        assertEquals(expected = 100, actual = metadata["total"])
        assertEquals(expected = 1, actual = metadata["page"])
    }

    @Test
    fun `resolves primitive return type correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("999")
        )

        val value = apiService.getPrimitive()
        assertEquals(expected = 999, actual = value)
    }

    @Test
    fun `serializes request body correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": 7, "name": "Eve", "isActive": true}""")
        )

        val newUser = RetrofitUser(id = 7, name = "Eve", isActive = true)
        val response = apiService.createUser(user = newUser)

        assertEquals(expected = "Eve", actual = response.name)

        val request = mockWebServer.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals(expected = """{"id":7,"name":"Eve","isActive":true}""", actual = requestBody)
    }

    @Test
    fun `throws robust exception on malformed json body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": 42, "name": "John Doe", "isActive": """)
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
