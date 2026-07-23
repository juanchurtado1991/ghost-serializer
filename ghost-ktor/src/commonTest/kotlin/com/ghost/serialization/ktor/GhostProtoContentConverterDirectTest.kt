package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
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
 * Direct unit tests for [GhostProtoContentConverter] -- mirrors
 * [GhostContentConverterDirectTest], since it shares the same structure (null-return contract,
 * scratch-buffer growth) but reads through [com.ghost.serialization.parser.GhostProtoJsonFlatReader]
 * instead.
 */
class GhostProtoContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(ProtoKtorEvent::class to ProtoKtorEventSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == ProtoKtorEvent::class) ProtoKtorEventSerializer as GhostSerializer<T> else null
        })
    }

    @Test
    fun serializeNullable_returnsNullForNullValue() = runTest {
        val converter = GhostProtoContentConverter()
        val result = converter.serializeNullable(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<ProtoKtorEvent>(),
            null
        )
        assertNull(result)
    }

    @Test
    fun serializeNullable_returnsNullForUnregisteredType() = runTest {
        val converter = GhostProtoContentConverter()
        val result = converter.serializeNullable(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<UnregisteredUser>(),
            UnregisteredUser(1, "x")
        )
        assertNull(result)
    }

    @Test
    fun deserialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostProtoContentConverter()
        val channel = ByteReadChannel("""{"deviceId":"1","label":"x"}""".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<UnregisteredUser>(), channel)
        assertNull(result)
    }

    @Test
    fun deserialize_growsScratchBufferForPayloadsLargerThanInitialSize() = runTest {
        val longLabel = "n".repeat(600_000)
        val json = """{"deviceId":"42","label":"$longLabel"}"""
        val converter = GhostProtoContentConverter()
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(Charsets.UTF_8, typeInfo<ProtoKtorEvent>(), channel)

        assertEquals(ProtoKtorEvent(42L, longLabel), result)
    }
}
