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

class GhostYamlContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(YamlKtorUser::class to YamlKtorUserSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == YamlKtorUser::class) YamlKtorUserSerializer as GhostSerializer<T> else null
        })
    }

    @Test
    fun serialize_returnsNullForNullValue() = runTest {
        val converter = GhostYamlContentConverter()
        val result = converter.serialize(
            contentType = GhostKtorMediaTypes.APPLICATION_YAML,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<YamlKtorUser>(),
            value = null
        )
        assertNull(actual = result)
    }

    @Test
    fun serialize_returnsNullForJsonOnlySerializer() = runTest {
        val converter = GhostYamlContentConverter()
        val result = converter.serialize(
            contentType = GhostKtorMediaTypes.APPLICATION_YAML,
            charset = Charsets.UTF_8,
            typeInfo = typeInfo<KtorUser>(),
            value = KtorUser(id = 1, name = "x", isActive = true)
        )
        assertNull(actual = result)
    }

    @Test
    fun deserialize_returnsNullForJsonOnlySerializer() = runTest {
        val converter = GhostYamlContentConverter()
        val channel = ByteReadChannel("id: 1\nname: x\nisActive: true\n".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channel)
        assertNull(actual = result)
    }

    @Test
    fun roundTripsYamlPayload() = runTest {
        val yaml = """
            id: 7
            name: "Zoe"
            isActive: true
        """.trimIndent()
        val converter = GhostYamlContentConverter()
        val channel = ByteReadChannel(yaml.encodeToByteArray())
        val decoded = converter.deserialize(Charsets.UTF_8, typeInfo<YamlKtorUser>(), channel)
        assertEquals(expected = YamlKtorUser(id = 7, name = "Zoe", isActive = true), actual = decoded)
    }
}
