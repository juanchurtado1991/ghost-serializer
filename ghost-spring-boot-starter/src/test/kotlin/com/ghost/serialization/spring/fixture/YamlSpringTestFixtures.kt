@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.spring.fixture

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KClass

data class YamlProfileMessage(val id: Int, val name: String)

object YamlProfileMessageSerializer :
    GhostSerializer<YamlProfileMessage>,
    GhostYamlSerializer<YamlProfileMessage> {
    override val typeName: String = "com.ghost.serialization.spring.fixture.YamlProfileMessage"

    override fun serialize(writer: GhostJsonWriter, value: YamlProfileMessage) = Unit
    override fun serialize(writer: GhostJsonFlatWriter, value: YamlProfileMessage) = Unit
    override fun deserialize(reader: GhostJsonReader): YamlProfileMessage = YamlProfileMessage(0, "")

    override fun serialize(writer: GhostYamlWriter, value: YamlProfileMessage) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: YamlProfileMessage) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): YamlProfileMessage {
        var id = 0
        var name = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextKey()) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return YamlProfileMessage(id, name)
    }
}

object YamlSpringTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(YamlProfileMessage::class to YamlProfileMessageSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == YamlProfileMessage::class) YamlProfileMessageSerializer as GhostSerializer<T> else null
}

@Configuration(proxyBeanMethods = false)
open class YamlSpringTestRegistryConfig {
    @PostConstruct
    fun registerYamlSerializers() {
        Ghost.addRegistry(YamlSpringTestRegistry)
    }
}
