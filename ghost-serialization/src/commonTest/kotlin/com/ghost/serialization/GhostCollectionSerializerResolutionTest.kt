package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private data class JsonOnlyDto(val id: Int)

private object JsonOnlyDtoSerializer : GhostSerializer<JsonOnlyDto> {
    override val typeName: String = "JsonOnlyDto"
    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonWriter, value: JsonOnlyDto) = Unit
    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter, value: JsonOnlyDto) = Unit
    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): JsonOnlyDto =
        JsonOnlyDto(0)
    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): JsonOnlyDto =
        JsonOnlyDto(0)
    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): JsonOnlyDto =
        JsonOnlyDto(0)
}

private data class YamlCapableDto(val id: Int)

private object YamlCapableDtoSerializer :
    GhostSerializer<YamlCapableDto>,
    GhostYamlSerializer<YamlCapableDto> {
    override val typeName: String = "YamlCapableDto"
    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonWriter, value: YamlCapableDto) = Unit
    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter, value: YamlCapableDto) = Unit
    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): YamlCapableDto =
        YamlCapableDto(0)
    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): YamlCapableDto =
        YamlCapableDto(0)
    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): YamlCapableDto =
        YamlCapableDto(0)
    override fun serialize(writer: com.ghost.serialization.writer.yaml.GhostYamlWriter, value: YamlCapableDto) = Unit
    override fun serialize(writer: com.ghost.serialization.writer.yaml.GhostYamlFlatWriter, value: YamlCapableDto) = Unit
    override fun deserialize(reader: com.ghost.serialization.parser.yaml.GhostYamlFlatReader): YamlCapableDto =
        YamlCapableDto(0)
}

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
