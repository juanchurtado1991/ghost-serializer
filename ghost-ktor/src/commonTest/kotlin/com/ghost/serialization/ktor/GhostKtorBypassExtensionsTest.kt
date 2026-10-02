@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Direct unit tests for [bodyGhost], [bodyGhostProto], and [bodyGhostYaml]: client bypass
 * extensions that deserialize without Ktor's `ContentNegotiation` pipeline.
 */
class GhostKtorBypassExtensionsTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = mapOf(
                KtorUser::class to KtorUserSerializer,
                ProtoKtorEvent::class to ProtoKtorEventSerializer,
                YamlKtorUser::class to YamlKtorUserSerializer,
            )

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                when (clazz) {
                    KtorUser::class -> KtorUserSerializer as GhostSerializer<T>
                    ProtoKtorEvent::class -> ProtoKtorEventSerializer as GhostSerializer<T>
                    YamlKtorUser::class -> YamlKtorUserSerializer as GhostSerializer<T>
                    else -> null
                }
        })
    }

    @Test
    fun bodyGhost_deserializesWithoutContentNegotiationInstalled() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":7,"name":"Zoe","isActive":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.JSON_CONTENT_TYPE)
            )
        }

        // No ContentNegotiation installed — bodyGhost must work against a bare HttpClient.
        val client = HttpClient(mockEngine)
        val response = client.get("/user").bodyGhost<KtorUser>()

        assertEquals(expected = 7, actual = response.id)
        assertEquals(expected = "Zoe", actual = response.name)
        assertTrue(actual = response.isActive)
    }

    @Test
    fun bodyGhost_throwsWithDescriptiveMessageForUnregisteredType() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":1,"name":"Ghost"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.JSON_CONTENT_TYPE)
            )
        }

        val client = HttpClient(mockEngine)
        val error = assertFailsWith<IllegalArgumentException> {
            client.get("/user").bodyGhost<UnregisteredUser>()
        }
        assertTrue(actual = error.message!!.contains(GhostKtorErrorMessages.SERIALIZER_NOT_FOUND_PREFIX))
        assertTrue(actual = error.message!!.contains("UnregisteredUser"))
    }

    @Test
    fun bodyGhostProto_throwsWithDescriptiveMessageForUnregisteredType() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"deviceId":"1","label":"x"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.JSON_CONTENT_TYPE)
            )
        }

        val client = HttpClient(mockEngine)
        val error = assertFailsWith<IllegalArgumentException> {
            client.get("/event").bodyGhostProto<UnregisteredUser>()
        }
        assertTrue(actual = error.message!!.contains(Ghost.NOT_FOUND))
        assertTrue(actual = error.message!!.contains("UnregisteredUser"))
    }

    @Test
    fun bodyGhostYaml_deserializesWithoutContentNegotiationInstalled() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    id: 7
                    name: "Zoe"
                    isActive: true
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, GhostKtorMediaTypes.YAML_CONTENT_TYPE)
            )
        }

        val client = HttpClient(mockEngine)
        val response = client.get("/user").bodyGhostYaml<YamlKtorUser>()

        assertEquals(expected = 7, actual = response.id)
        assertEquals(expected = "Zoe", actual = response.name)
        assertTrue(actual = response.isActive)
    }
}
