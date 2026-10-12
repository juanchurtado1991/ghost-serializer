package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.ByteArrayContent
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.encodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The negotiated charset is honored on both directions instead of assuming UTF-8. */
class GhostKtorCharsetTest {

    private val user = KtorUser(id = 3, name = "José Ñandú", isActive = true)
    private val json = """{"id":3,"name":"José Ñandú","isActive":true}"""

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
    fun deserializeDecodesIso88591Body() = runTest {
        val bytes = Charsets.ISO_8859_1.newEncoder().encodeToByteArray(json)
        val result = GhostContentConverter().deserialize(
            Charsets.ISO_8859_1,
            typeInfo<KtorUser>(),
            ByteReadChannel(bytes)
        )
        assertEquals(expected = user, actual = result)
    }

    @Test
    fun serializeEncodesIso88591AndDeclaresIt() = runTest {
        val content = GhostContentConverter().serialize(
            contentType = ContentType.Application.Json,
            charset = Charsets.ISO_8859_1,
            typeInfo = typeInfo<KtorUser>(),
            value = user
        )
        val bytes = assertNotNull(actual = (content as ByteArrayContent).bytes())
        assertEquals(expected = Charsets.ISO_8859_1, actual = content.contentType?.charset())
        assertEquals(expected = Charsets.ISO_8859_1.newEncoder().encodeToByteArray(json).toList(), actual = bytes.toList())
    }

    @Test
    fun serializeKeepsUtf8ContentTypeUntouched() = runTest {
        val content = GhostContentConverter().serialize(
            contentType = ContentType.Application.Json,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<KtorUser>(),
            value = user
        )
        assertEquals(expected = ContentType.Application.Json, actual = (content as ByteArrayContent).contentType)
    }
}
