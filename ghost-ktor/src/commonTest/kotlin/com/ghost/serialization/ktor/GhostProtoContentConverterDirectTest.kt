package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.http.ContentType
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Direct unit tests for [GhostProtoContentConverter]: proto3 JSON read path, null-return
 * contract, scratch-buffer growth, and list deserialization via `GhostProtoJsonFlatReader`.
 */
class GhostProtoContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(ProtoKtorEvent::class to ProtoKtorEventSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == ProtoKtorEvent::class) ProtoKtorEventSerializer as GhostSerializer<T> else null
        })
    }

    @Test
    fun serialize_returnsNullForNullValue() = runTest {
        val converter = GhostProtoContentConverter()
        val result = converter.serialize(
            contentType = ContentType.Application.Json,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<ProtoKtorEvent>(),
            value = null
        )
        assertNull(actual = result)
    }

    @Test
    fun serialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostProtoContentConverter()
        val result = converter.serialize(
            contentType = ContentType.Application.Json,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<UnregisteredUser>(),
            value = UnregisteredUser(id = 1, name = "x")
        )
        assertNull(actual = result)
    }

    @Test
    fun deserialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostProtoContentConverter()
        val channel = ByteReadChannel("""{"deviceId":"1","label":"x"}""".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<UnregisteredUser>(), channel)
        assertNull(actual = result)
    }

    @Test
    fun deserialize_growsScratchBufferForPayloadsLargerThanInitialSize() = runTest {
        val longLabel = "n".repeat(600_000)
        val json = """{"deviceId":"42","label":"$longLabel"}"""
        val converter = GhostProtoContentConverter()
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(Charsets.UTF_8, typeInfo<ProtoKtorEvent>(), channel)

        assertEquals(expected = ProtoKtorEvent(deviceId = 42L, label = longLabel), actual = result)
    }

    @Test
    fun deserialize_parsesListBodyViaKotlinType() = runTest {
        val converter = GhostProtoContentConverter()
        val json = """[{"deviceId":"5","label":"batch"}]"""
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(
            Charsets.UTF_8,
            typeInfo<List<ProtoKtorEvent>>(),
            channel,
        ) as List<ProtoKtorEvent>

        assertEquals(expected = listOf(ProtoKtorEvent(deviceId = 5L, label = "batch")), actual = result)
    }

    @Test
    fun deserialize_parsesBareInt64InsideListElements() = runTest {
        val converter = GhostProtoContentConverter()
        val json = """[{"deviceId":9223372036854775807,"label":"max"}]"""
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(
            Charsets.UTF_8,
            typeInfo<List<ProtoKtorEvent>>(),
            channel,
        ) as List<ProtoKtorEvent>

        assertEquals(expected = Long.MAX_VALUE, actual = result.single().deviceId)
    }
}
