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
 * Direct unit tests for [GhostContentConverter] — null-return contract, scratch-buffer growth
 * for payloads larger than the initial buffer, and round-trip deserialization without a Ktor
 * client or server.
 */
class GhostContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(KtorUser::class to KtorUserSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == KtorUser::class) KtorUserSerializer as GhostSerializer<T> else null
        })
    }

    @Test
    fun serialize_returnsNullForNullValue() = runTest {
        val converter = GhostContentConverter()
        val result = converter.serialize(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<KtorUser>(),
            null
        )
        assertNull(result)
    }

    @Test
    fun serialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostContentConverter()
        val result = converter.serialize(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<UnregisteredUser>(),
            UnregisteredUser(1, "x")
        )
        assertNull(result)
    }

    @Test
    fun deserialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostContentConverter()
        val channel = ByteReadChannel("""{"id":1,"name":"x"}""".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<UnregisteredUser>(), channel)
        assertNull(result)
    }

    @Test
    fun deserialize_growsScratchBufferForPayloadsLargerThanInitialSize() = runTest {
        // BUFFER_SIZE is 524288 (512 KB); a name comfortably larger than that forces at least
        // one grow-and-copy cycle in the read loop.
        val longName = "n".repeat(600_000)
        val json = """{"id":1,"name":"$longName"}"""
        val converter = GhostContentConverter()
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channel)

        assertEquals(KtorUser(1, longName, false), result)
    }

    @Test
    fun deserialize_parsesSetBodyViaKotlinType() = runTest {
        val converter = GhostContentConverter()
        val channel = ByteReadChannel("""[{"id":1,"name":"a"}]""".encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<Set<KtorUser>>(), channel)
            as Set<KtorUser>
        assertEquals(setOf(KtorUser(1, "a", false)), result)
    }
}
