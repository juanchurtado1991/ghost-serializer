package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.writer.GhostJsonWriter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// --- Mock Models ---
data class KtorUser(val id: Int, val name: String, val isActive: Boolean)

// --- Manual Serializer for Testing ---
object KtorUserSerializer : GhostSerializer<KtorUser> {
    override val typeName: String = "com.ghost.serialization.ktor.KtorUser"
    
    override fun serialize(writer: GhostJsonWriter, value: KtorUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id.toLong())
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): KtorUser {
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
        return KtorUser(id, name, isActive)
    }
}

class GhostKtorTest {

    @BeforeTest
    fun setup() {
        // Register mock serializer into the Ghost runtime
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(KtorUser::class to KtorUserSerializer)
            }
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
                if (clazz == KtorUser::class) return KtorUserSerializer as GhostSerializer<T>
                return null
            }
        })
    }

    @Test
    fun testSuccessfulSerializationAndDeserialization() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/user", request.url.encodedPath)
            respond(
                content = """{"id": 42, "name": "John", "isActive": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
            }
        }

        val response: KtorUser = client.get("/user").body()
        assertEquals(42, response.id)
        assertEquals("John", response.name)
        assertEquals(true, response.isActive)
    }

    @Test
    fun testSerializationOfRequestBody() = runTest {
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as io.ktor.http.content.TextContent).text
            assertEquals("""{"id":100,"name":"Alice","isActive":false}""", bodyText)
            respond(
                content = bodyText,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
            }
        }

        val response: KtorUser = client.post("/user") {
            contentType(ContentType.Application.Json)
            setBody(KtorUser(100, "Alice", false))
        }.body()

        assertEquals(100, response.id)
    }

    @Test
    fun testMalformedPayloadThrowsException() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id": 42, "name": "John", "isActive": """, // Incomplete JSON
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
            }
        }

        assertFailsWith<Exception> {
            client.get("/user").body<KtorUser>()
        }
    }

    @Test
    fun testCancellationMidStream() = runTest {
        val mockEngine = MockEngine {
            // Simulate a slow response that will be cancelled
            delay(1000)
            respond(
                content = """{"id": 42}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
            }
        }

        val job = launch {
            assertFailsWith<CancellationException> {
                client.get("/slow").body<KtorUser>()
            }
        }

        delay(100)
        job.cancel()
    }

    @Test
    fun test204NoContentReturnsNullOrFails() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
            }
        }

        assertFailsWith<Exception> {
            // Should fail because body is empty but type is not nullable
            client.get("/empty").body<KtorUser>()
        }
    }
}
