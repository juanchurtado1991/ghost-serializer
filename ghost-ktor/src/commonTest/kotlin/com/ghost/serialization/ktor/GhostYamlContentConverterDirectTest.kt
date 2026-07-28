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

class GhostYamlContentConverterDirectTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
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
            ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML),
            Charsets.UTF_8,
            typeInfo<YamlKtorUser>(),
            null
        )
        assertNull(result)
    }

    @Test
    fun serialize_returnsNullForJsonOnlySerializer() = runTest {
        val converter = GhostYamlContentConverter()
        val result = converter.serialize(
            ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML),
            Charsets.UTF_8,
            typeInfo<KtorUser>(),
            KtorUser(1, "x", true)
        )
        assertNull(result)
    }

    @Test
    fun deserialize_returnsNullForJsonOnlySerializer() = runTest {
        val converter = GhostYamlContentConverter()
        val channel = ByteReadChannel("id: 1\nname: x\nisActive: true\n".encodeToByteArray())
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<KtorUser>(), channel)
        assertNull(result)
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
        assertEquals(YamlKtorUser(7, "Zoe", true), decoded)
    }
}
