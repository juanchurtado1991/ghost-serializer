package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [respondGhost]/[respondGhostProto] (`ApplicationCall` extensions) bypass Ktor server's
 * `ContentNegotiation` pipeline entirely — they need a real request/response cycle, unlike
 * the client-side bypass extensions covered by [GhostKtorBypassExtensionsTest] (commonTest).
 * JVM-only because `ktor-server-test-host` doesn't ship for Kotlin/Native targets.
 */
class GhostKtorServerExtensionsTest {

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
    fun respondGhost_writesGhostEncodedBodyWithJsonContentType() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(KtorUser(id = 42, name = "John", isActive = true))
            }
        }

        val response = client.get("/user")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("application/json", response.headers["Content-Type"]?.substringBefore(";"))
        assertEquals("""{"id":42,"name":"John","isActive":true}""", response.bodyAsText())
    }

    @Test
    fun respondGhost_honorsCustomStatusCode() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(KtorUser(id = 1, name = "Ada", isActive = false), HttpStatusCode.Created)
            }
        }

        val response = client.get("/user")

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun respondGhost_unregisteredTypeThrowsDescriptiveException() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(UnregisteredUser(id = 1, name = "X"))
            }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            client.get("/user")
        }
        assertTrue(error.message!!.contains(ERROR_PREFIX))
        assertTrue(error.message!!.contains("UnregisteredUser"))
    }

    @Test
    fun respondGhostProto_writesProtoWireFormat() = testApplication {
        routing {
            get("/event") {
                call.respondGhostProto(ProtoKtorEvent(deviceId = Long.MAX_VALUE, label = "sensor-1"))
            }
        }

        val response = client.get("/event")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"deviceId":"9223372036854775807","label":"sensor-1"}""", response.bodyAsText())
    }
}
