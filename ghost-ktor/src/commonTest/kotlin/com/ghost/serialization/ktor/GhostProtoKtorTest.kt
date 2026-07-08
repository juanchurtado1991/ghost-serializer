@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// --- Mock proto-flavored model & hand-written stand-in for @GhostProtoSerialization codegen ---
data class ProtoKtorEvent(val deviceId: Long, val label: String)

object ProtoKtorEventSerializer : GhostSerializer<ProtoKtorEvent> {
    override val typeName: String = "com.ghost.serialization.ktor.ProtoKtorEvent"

    override fun serialize(writer: GhostJsonWriter, value: ProtoKtorEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoKtorEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoKtorEvent {
        var deviceId = 0L
        var label = ""
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "deviceId" -> deviceId = reader.nextLong()
                "label" -> label = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ProtoKtorEvent(deviceId, label)
    }

    // Explicit (not the default interface bridge) so a GhostProtoJsonFlatReader dispatches
    // reader.nextLong() to its overridden, proto3-lenient implementation.
    override fun deserialize(reader: GhostJsonFlatReader): ProtoKtorEvent {
        var deviceId = 0L
        var label = ""
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "deviceId" -> deviceId = reader.nextLong()
                "label" -> label = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ProtoKtorEvent(deviceId, label)
    }
}

class GhostProtoKtorTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(ProtoKtorEvent::class to ProtoKtorEventSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
                if (clazz == ProtoKtorEvent::class) return ProtoKtorEventSerializer as GhostSerializer<T>
                return null
            }
        })
    }

    @Test
    fun `parses a quoted int64 response via ContentNegotiation`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"deviceId":"9223372036854775807","label":"sensor-1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                register(io.ktor.http.ContentType.Application.Json, GhostProtoContentConverter())
            }
        }

        val response: ProtoKtorEvent = client.get("/event").body()
        assertEquals(Long.MAX_VALUE, response.deviceId)
        assertEquals("sensor-1", response.label)
    }

    @Test
    fun `bodyGhostProto parses a quoted int64 bypassing ContentNegotiation`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"deviceId":"42","label":"sensor-2"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val response = client.get("/event").bodyGhostProto<ProtoKtorEvent>()
        assertEquals(42L, response.deviceId)
        assertEquals("sensor-2", response.label)
    }
}
