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
 * Direct unit tests for [GhostContentConverter]: null-return contract, scratch-buffer growth,
 * and round-trip deserialization without a Ktor client/server.
 */
class GhostContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
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
            contentType = ContentType.Application.Json,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<KtorUser>(),
            value = null
        )
        assertNull(actual = result)
    }

    @Test
    fun serialize_returnsNullForUnregisteredType() = runTest {
        val converter = GhostContentConverter()
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
        val converter = GhostContentConverter()
        val channel = ByteReadChannel("""{"id":1,"name":"x"}""".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<UnregisteredUser>(), channel)
        assertNull(actual = result)
    }

    @Test
    fun deserialize_growsScratchBufferForPayloadsLargerThanInitialSize() = runTest {
        // Larger than the 512 KB initial buffer, forcing a grow-and-copy cycle.
        val longName = "n".repeat(600_000)
        val json = """{"id":1,"name":"$longName"}"""
        val converter = GhostContentConverter()
        val channel = ByteReadChannel(json.encodeToByteArray())

        val result = converter.deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channel)

        assertEquals(expected = KtorUser(id = 1, name = longName, isActive = false), actual = result)
    }

    @Test
    fun deserialize_parsesSetBodyViaKotlinType() = runTest {
        val converter = GhostContentConverter()
        val channel = ByteReadChannel("""[{"id":1,"name":"a"}]""".encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<Set<KtorUser>>(), channel)
            as Set<KtorUser>
        assertEquals(expected = setOf(KtorUser(id = 1, name = "a", isActive = false)), actual = result)
    }
}
