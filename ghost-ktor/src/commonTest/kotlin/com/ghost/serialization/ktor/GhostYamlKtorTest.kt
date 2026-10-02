@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
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
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostYamlKtorTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(YamlKtorUser::class to YamlKtorUserSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == YamlKtorUser::class) YamlKtorUserSerializer as GhostSerializer<T> else null
        })
    }

    @Test
    fun deserializesYamlResponseViaContentNegotiation() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    id: 42
                    name: "John"
                    isActive: true
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.APPLICATION_YAML.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { ghostYaml() }
        }

        val response: YamlKtorUser = client.get("/user").body()
        assertEquals(expected = 42, actual = response.id)
        assertEquals(expected = "John", actual = response.name)
        assertEquals(expected = true, actual = response.isActive)
    }

    @Test
    fun serializesYamlRequestBody() = runTest {
        val mockEngine = MockEngine { request ->
            val bodyText = when (val body = request.body) {
                is io.ktor.http.content.TextContent -> body.text
                is io.ktor.http.content.OutgoingContent.ByteArrayContent -> body.bytes()
                    .decodeToString()

                else -> error("Unsupported body type: ${body::class}")
            }
            assertTrue(actual = bodyText.contains("id: 100"))
            assertTrue(actual = bodyText.contains("name: \"Alice\"") || bodyText.contains("name: Alice"))
            assertTrue(actual = bodyText.contains("isActive: false"))
            respond(
                content = bodyText,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.APPLICATION_YAML.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { ghostYaml() }
        }

        val response: YamlKtorUser = client.post("/user") {
            contentType(GhostKtorMediaTypes.APPLICATION_YAML)
            setBody(YamlKtorUser(id = 100, name = "Alice", isActive = false))
        }.body()

        assertEquals(expected = 100, actual = response.id)
    }
}
