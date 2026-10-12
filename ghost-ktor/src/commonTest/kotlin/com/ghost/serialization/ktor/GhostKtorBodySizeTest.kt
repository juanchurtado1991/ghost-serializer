package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.exception.GhostJsonException
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Empty and multi-megabyte request/response bodies on the JSON, YAML and proto converters. */
class GhostKtorBodySizeTest {

    private val bigText = "n".repeat(3_000_000)

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            private val serializers: Map<KClass<*>, GhostSerializer<*>> = mapOf(
                KtorUser::class to KtorUserSerializer,
                YamlKtorUser::class to YamlKtorUserSerializer,
                ProtoKtorEvent::class to ProtoKtorEventSerializer
            )

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = serializers

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                serializers[clazz] as GhostSerializer<T>?
        })
    }

    private fun channelOf(text: String) = ByteReadChannel(text.encodeToByteArray())

    @Test
    fun jsonEmptyBodyIsNull() = runTest {
        assertNull(actual = GhostContentConverter().deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channelOf("")))
    }

    @Test
    fun jsonBlankBodyThrows() = runTest {
        assertFailsWith<GhostJsonException> {
            GhostContentConverter().deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channelOf("  "))
        }
    }

    @Test
    fun jsonLargeBodyRoundTrips() = runTest {
        val decoded = GhostContentConverter().deserialize(
            Charsets.UTF_8,
            typeInfo<KtorUser>(),
            channelOf("{\"id\":1,\"name\":\"$bigText\",\"isActive\":true}")
        )
        assertEquals(expected = KtorUser(id = 1, name = bigText, isActive = true), actual = decoded)
    }

    @Test
    fun protoEmptyBodyThrows() = runTest {
        assertFailsWith<GhostJsonException> {
            GhostProtoContentConverter().deserialize(Charsets.UTF_8, typeInfo<ProtoKtorEvent>(), channelOf(""))
        }
    }

    @Test
    fun yamlLargeBodyRoundTrips() = runTest {
        val decoded = GhostYamlContentConverter().deserialize(
            Charsets.UTF_8,
            typeInfo<YamlKtorUser>(),
            channelOf("id: 1\nname: $bigText\nisActive: true")
        )
        assertEquals(expected = YamlKtorUser(id = 1, name = bigText, isActive = true), actual = decoded)
    }

    @Test
    fun yamlEmptyBodyReadsAsAnEmptyMapping() = runTest {
        val converter = GhostYamlContentConverter()
        assertEquals(
            expected = converter.deserialize(Charsets.UTF_8, typeInfo<YamlKtorUser>(), channelOf("{}")),
            actual = converter.deserialize(Charsets.UTF_8, typeInfo<YamlKtorUser>(), channelOf(""))
        )
    }

    @Test
    fun largeValueEncodesAndDecodesBack() = runTest {
        val converter = GhostContentConverter()
        val content = converter.serialize(
            contentType = io.ktor.http.ContentType.Application.Json,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<KtorUser>(),
            value = KtorUser(id = 2, name = bigText, isActive = false)
        ) as io.ktor.http.content.ByteArrayContent
        val decoded = converter.deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), ByteReadChannel(content.bytes()))
        assertEquals(expected = KtorUser(id = 2, name = bigText, isActive = false), actual = decoded)
    }
}
