package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostCollectionSerializerResolutionTest {

    @Test
    fun getSerializer_listOfJsonOnlyDtoUsesListSerializer() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(JsonOnlyDto::class to JsonOnlyDtoSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == JsonOnlyDto::class) JsonOnlyDtoSerializer as GhostSerializer<T> else null
        })

        val serializer = Ghost.getSerializer(typeOf<List<JsonOnlyDto>>())
        assertNotNull(serializer)
        assertTrue(serializer is ListSerializer<*>)
    }

    @Test
    fun getSerializer_listOfYamlCapableDtoUsesGhostYamlListSerializer() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(YamlCapableDto::class to YamlCapableDtoSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == YamlCapableDto::class) YamlCapableDtoSerializer as GhostSerializer<T> else null
        })

        val serializer = Ghost.getSerializer(typeOf<List<YamlCapableDto>>())
        assertNotNull(serializer)
        assertTrue(serializer is GhostYamlListSerializer<*>)
    }

    @Test
    fun getSerializer_mapOfYamlCapableDtoUsesGhostYamlMapSerializer() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(YamlCapableDto::class to YamlCapableDtoSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == YamlCapableDto::class) YamlCapableDtoSerializer as GhostSerializer<T> else null
        })

        val serializer = Ghost.getSerializer(typeOf<Map<String, YamlCapableDto>>())
        assertNotNull(serializer)
        assertTrue(serializer is GhostYamlMapSerializer<*>)
    }

    @Test
    fun getSerializer_mapOfJsonOnlyDtoUsesMapSerializer() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
                mapOf(JsonOnlyDto::class to JsonOnlyDtoSerializer)

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                if (clazz == JsonOnlyDto::class) JsonOnlyDtoSerializer as GhostSerializer<T> else null
        })

        val serializer = Ghost.getSerializer(typeOf<Map<String, JsonOnlyDto>>())
        assertNotNull(serializer)
        assertTrue(serializer is MapSerializer<*>)
    }
}
