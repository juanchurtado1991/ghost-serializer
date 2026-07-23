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
 * Direct unit tests for [GhostContentConverter] -- no Ktor client/server needed, since it's a
 * plain `ContentConverter`. [GhostKtorTest] only ever exercises it end-to-end through a real
 * `ContentNegotiation` pipeline with small payloads, so the null-return contract (both
 * directions) and the scratch-buffer growth path (payload > 512 KB) had no coverage.
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
    fun serializeNullable_returnsNullForNullValue() = runTest {
        val converter = GhostContentConverter()
        val result = converter.serializeNullable(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<KtorUser>(),
            null
        )
        assertNull(result)
    }

    @Test
    fun serializeNullable_returnsNullForUnregisteredType() = runTest {
        val converter = GhostContentConverter()
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
}
