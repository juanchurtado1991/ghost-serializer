@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhostKtorTest {

    @BeforeTest
    fun setup() {
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
            val bodyText = when (val body = request.body) {
                is io.ktor.http.content.TextContent -> body.text
                is io.ktor.http.content.OutgoingContent.ByteArrayContent -> body.bytes()
                    .decodeToString()

                else -> error("Unsupported body type: ${body::class}")
            }
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
                content = """{"id": 42, "name": "John", "isActive": """, // incomplete
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
            delay(1000) // slow enough to cancel mid-request
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
            // Body is empty but KtorUser is not nullable
            client.get("/empty").body<KtorUser>()
        }
    }

    @Test
    fun testStrictConfiguredKtorConverterThrowsOnMissingComma() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":42 "name":"John", "isActive":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter { reader ->
                    reader.strictMode = true
                })
            }
        }

        assertFailsWith<Exception> {
            client.get("/user").body<KtorUser>()
        }
    }

    @Test
    fun testCoercedConfiguredKtorConverterCoercesValues() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":"42", "name":"John", "isActive":"true"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter { reader ->
                    reader.coerceStringsToNumbers = true
                    reader.coerceBooleans = true
                })
            }
        }

        val user: KtorUser = client.get("/user").body()
        assertEquals(42, user.id)
        assertEquals("John", user.name)
        assertEquals(true, user.isActive)
    }

    @Test
    fun testCoexistenceFallback() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":999,"name":"Fallback"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val fallbackConverter = object : ContentConverter {
            override suspend fun serialize(
                contentType: ContentType,
                charset: Charset,
                typeInfo: TypeInfo,
                value: Any?
            ): OutgoingContent? = null

            override suspend fun deserialize(
                charset: Charset,
                typeInfo: TypeInfo,
                content: ByteReadChannel
            ): Any? {
                if (typeInfo.type != UnregisteredUser::class) return null
                return UnregisteredUser(999, "Fallback")
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, GhostContentConverter())
                register(ContentType.Application.Json, fallbackConverter)
            }
        }

        val user: UnregisteredUser = client.get("/user").body()
        assertEquals(999, user.id)
        assertEquals("Fallback", user.name)
    }
}
