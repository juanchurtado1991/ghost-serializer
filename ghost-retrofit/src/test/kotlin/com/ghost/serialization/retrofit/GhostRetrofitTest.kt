package com.ghost.serialization.retrofit

import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.core.writer.GhostJsonWriter
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
object RetrofitUserSerializer : GhostSerializer<RetrofitUser> {
    override val typeName: String = "com.ghost.serialization.retrofit.RetrofitUser"
    
    override fun serialize(writer: GhostJsonWriter, value: RetrofitUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id.toLong())
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
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
                else -> reader.skipAnyValue()
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
