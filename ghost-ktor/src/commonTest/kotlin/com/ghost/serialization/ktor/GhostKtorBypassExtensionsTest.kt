@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
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
 * [bodyGhost]/[bodyGhostProto] bypass Ktor's `ContentNegotiation` pipeline entirely, so they
 * need coverage independent of [GhostKtorTest]/[GhostProtoKtorTest], which only exercise the
 * `ContentNegotiation`-routed path (`.body()` via [GhostContentConverter]/[GhostProtoContentConverter]).
 * Reuses the `KtorUser`/`UnregisteredUser`/`ProtoKtorEvent` fixtures declared in those files
 * (same package, same source set).
 */
class GhostKtorBypassExtensionsTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = mapOf(
                KtorUser::class to KtorUserSerializer,
                ProtoKtorEvent::class to ProtoKtorEventSerializer
            )

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? = when (clazz) {
                KtorUser::class -> KtorUserSerializer as GhostSerializer<T>
                ProtoKtorEvent::class -> ProtoKtorEventSerializer as GhostSerializer<T>
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
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // No `install(ContentNegotiation)` — bodyGhost must work against a bare HttpClient.
        val client = HttpClient(mockEngine)
        val response = client.get("/user").bodyGhost<KtorUser>()

        assertEquals(7, response.id)
        assertEquals("Zoe", response.name)
        assertTrue(response.isActive)
    }

    @Test
    fun bodyGhost_throwsWithDescriptiveMessageForUnregisteredType() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"id":1,"name":"Ghost"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val error = assertFailsWith<IllegalArgumentException> {
            client.get("/user").bodyGhost<UnregisteredUser>()
        }
        assertTrue(error.message!!.contains(CLIENT_ERROR_PREFIX))
        assertTrue(error.message!!.contains("UnregisteredUser"))
    }

    @Test
    fun bodyGhostProto_throwsWithDescriptiveMessageForUnregisteredType() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"deviceId":"1","label":"x"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val error = assertFailsWith<IllegalArgumentException> {
            client.get("/event").bodyGhostProto<UnregisteredUser>()
        }
        assertTrue(error.message!!.contains(Ghost.NOT_FOUND))
        assertTrue(error.message!!.contains("UnregisteredUser"))
    }
}
