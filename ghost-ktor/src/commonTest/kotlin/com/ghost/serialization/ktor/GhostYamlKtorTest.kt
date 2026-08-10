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
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val YAML_MEDIA_TYPE = ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML)

class GhostYamlKtorTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
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
                headers = headersOf(HttpHeaders.ContentType, YAML_MEDIA_TYPE.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { ghostYaml() }
        }

        val response: YamlKtorUser = client.get("/user").body()
        assertEquals(42, response.id)
        assertEquals("John", response.name)
        assertEquals(true, response.isActive)
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
            assertTrue(bodyText.contains("id: 100"))
            assertTrue(bodyText.contains("name: \"Alice\"") || bodyText.contains("name: Alice"))
            assertTrue(bodyText.contains("isActive: false"))
            respond(
                content = bodyText,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, YAML_MEDIA_TYPE.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { ghostYaml() }
        }

        val response: YamlKtorUser = client.post("/user") {
            contentType(YAML_MEDIA_TYPE)
            setBody(YamlKtorUser(100, "Alice", false))
        }.body()

        assertEquals(100, response.id)
    }
}
