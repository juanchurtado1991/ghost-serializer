package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML map body serializer for `Map<String, V>` endpoints when the value serializer implements
 * [GhostYamlSerializer].
 */
class GhostYamlMapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>,
) : GhostSerializer<Map<String, V>>, GhostYamlSerializer<Map<String, V>> {

    @Suppress("UNCHECKED_CAST")
    private val yamlValue: GhostYamlSerializer<V> = run {
        require(valueSerializer is GhostYamlSerializer<*>) {
            C.ERR_YAML_MAP_NEEDS_YAML_VALUE_PREFIX + valueSerializer.typeName
        }
        valueSerializer as GhostYamlSerializer<V>
    }

    private val jsonMap = MapSerializer(valueSerializer)

    override val typeName: String
        get() = "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(
        writer: GhostJsonWriter,
        value: Map<String, V>
    ) =
        jsonMap.serialize(writer, value)

    override fun serialize(
        writer: GhostJsonFlatWriter,
        value: Map<String, V>
    ) =
        jsonMap.serialize(writer, value)

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: Map<String, V>
    ) =
        jsonMap.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: Map<String, V>) {
        writer.beginObject()
        for ((key, entryValue) in value) {
            writer.name(key)
            yamlValue.serialize(writer, entryValue)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: Map<String, V>) {
        writer.beginObject()
        for ((key, entryValue) in value) {
            writer.name(key)
            yamlValue.serialize(writer, entryValue)
        }
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: GhostJsonFlatReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: GhostJsonStringReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): Map<String, V> =
        reader.readMap({ reader.nextKey()!! }) { yamlValue.deserialize(reader) }
}
