@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.ignore
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonWriter
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
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// --- Mock Models ---
data class RetrofitUser(val id: Int, val name: String, val isActive: Boolean)

// --- Manual Serializer for Testing (avoiding KSP in this module) ---
@InternalGhostApi
object RetrofitUserSerializer : GhostSerializer<RetrofitUser> {
    override val typeName: String = "com.ghost.serialization.retrofit.RetrofitUser"
    
    override fun serialize(writer: GhostJsonWriter, value: RetrofitUser) {
        writer.beginObject().ignore()
        writer.name("id").ignore()
        writer.value(value.id.toLong()).ignore()
        writer.name("name").ignore()
        writer.value(value.name).ignore()
        writer.name("isActive").ignore()
        writer.value(value.isActive).ignore()
        writer.endObject().ignore()
    }

    override fun deserialize(reader: GhostJsonReader): RetrofitUser {
        var id = 0
        var name = ""
        var isActive = false
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                "isActive" -> isActive = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RetrofitUser(id, name, isActive)
    }
}

// --- Retrofit API Definition ---
interface MockApiService {
    @GET("/user")
    suspend fun getUser(): RetrofitUser

    @GET("/users")
    suspend fun getUsers(): List<RetrofitUser>

    @GET("/metadata")
    suspend fun getMetadata(): Map<String, Int>

    @POST("/user")
    suspend fun createUser(@Body user: RetrofitUser): RetrofitUser
    
    @GET("/primitive")
    suspend fun getPrimitive(): Int
}

class GhostRetrofitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MockApiService
    private lateinit var registry: GhostRegistry

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create a dedicated mock registry for testing
        registry = object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(RetrofitUser::class to RetrofitUserSerializer)
            }
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
                if (clazz == RetrofitUser::class) return RetrofitUserSerializer as GhostSerializer<T>
                return null
            }
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostConverterFactory.create(registry))
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
        
        // Verify that the request was made correctly
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
                .setBody("""{"id": 7, "name": "Eve", "isActive": true}""") // Echo back
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
