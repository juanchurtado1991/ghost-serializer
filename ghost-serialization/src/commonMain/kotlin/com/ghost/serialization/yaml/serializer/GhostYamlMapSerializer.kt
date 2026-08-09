package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML map body serializer for `Map<String, V>` endpoints when the value serializer implements
 * [GhostYamlSerializer].
 */
class GhostYamlMapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>,
) : GhostSerializer<Map<String, V>>, GhostYamlSerializer<Map<String, V>> {

    init {
        require(valueSerializer is GhostYamlSerializer<*>) {
            "GhostYamlMapSerializer requires a GhostYamlSerializer value serializer, got ${valueSerializer.typeName}"
        }
    }

    private val yamlValue: GhostYamlSerializer<V>
        get() = valueSerializer as GhostYamlSerializer<V>

    private val jsonMap = MapSerializer(valueSerializer)

    override val typeName: String
        get() = "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: Map<String, V>
    ) =
        jsonMap.serialize(writer, value)

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
        value: Map<String, V>
    ) =
        jsonMap.serialize(writer, value)

    override fun serialize(
        writer: com.ghost.serialization.writer.strings.GhostJsonStringWriter,
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

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): Map<String, V> =
        reader.readMap({ reader.nextKey()!! }) { yamlValue.deserialize(reader) }
}
