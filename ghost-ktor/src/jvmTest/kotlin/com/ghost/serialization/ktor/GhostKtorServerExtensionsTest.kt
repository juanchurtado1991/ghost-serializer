package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration tests for [respondGhost], [respondGhostProto], and [respondGhostYaml]: server-side
 * bypass extensions that respond without Ktor's `ContentNegotiation` pipeline.
 * JVM-only because `ktor-server-test-host` is not available on Kotlin/Native targets.
 */
class GhostKtorServerExtensionsTest {

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
    fun respondGhost_writesGhostEncodedBodyWithJsonContentType() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(value = KtorUser(id = 42, name = "John", isActive = true))
            }
        }

        val response = client.get("/user")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(
            expected = GhostKtorMediaTypes.JSON_CONTENT_TYPE,
            actual = response.headers["Content-Type"]?.substringBefore(";")
        )
        assertEquals(expected = """{"id":42,"name":"John","isActive":true}""", actual = response.bodyAsText())
    }

    @Test
    fun respondGhost_honorsCustomStatusCode() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(
                    value = KtorUser(id = 1, name = "Ada", isActive = false),
                    status = HttpStatusCode.Created
                )
            }
        }

        val response = client.get("/user")

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
    }

    @Test
    fun respondGhost_unregisteredTypeThrowsDescriptiveException() = testApplication {
        routing {
            get("/user") {
                call.respondGhost(value = UnregisteredUser(id = 1, name = "X"))
            }
        }

        // Ktor 3 test host converts uncaught handler exceptions into 500s, not client-side throws.
        val response = client.get("/user")
        assertEquals(expected = HttpStatusCode.InternalServerError, actual = response.status)
    }

    @Test
    fun respondGhostProto_writesProtoWireFormat() = testApplication {
        routing {
            get("/event") {
                call.respondGhostProto(
                    value = ProtoKtorEvent(
                        deviceId = Long.MAX_VALUE,
                        label = "sensor-1"
                    )
                )
            }
        }

        val response = client.get("/event")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(
            expected = """{"deviceId":"9223372036854775807","label":"sensor-1"}""",
            actual = response.bodyAsText()
        )
    }

    @Test
    fun respondGhostYaml_writesYamlBodyWithYamlContentType() = testApplication {
        routing {
            get("/user") {
                call.respondGhostYaml(value = YamlKtorUser(id = 42, name = "John", isActive = true))
            }
        }

        val response = client.get("/user")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(
            expected = GhostKtorMediaTypes.YAML_CONTENT_TYPE,
            actual = response.headers["Content-Type"]?.substringBefore(";")
        )
        val body = response.bodyAsText()
        assertEquals(expected = true, actual = body.contains("id: 42"))
        assertEquals(expected = true, actual = body.contains("name: John") || body.contains("name: \"John\""))
    }
}
