package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML map body serializer for `Map<String, V>` endpoints. [valueSerializer]'s type is
 * constrained to implement both [GhostSerializer] and [GhostYamlSerializer] at compile time, so
 * no runtime check/cast is needed to use it on either channel.
 */
class GhostYamlMapSerializer<V, S>(private val valueSerializer: S) :
    AbstractGhostSerializer<Map<String, V>>(),
    GhostYamlSerializer<Map<String, V>> where S : GhostSerializer<V>, S : GhostYamlSerializer<V> {

    private val jsonMap = MapSerializer(valueSerializer = valueSerializer)

    override val typeName: String
        get() = "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(writer: GhostJsonWriter, value: Map<String, V>) =
        jsonMap.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostJsonStringWriter, value: Map<String, V>) =
        jsonMap.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostYamlWriter, value: Map<String, V>) {
        writer.beginObject()
        for ((key, entryValue) in value) {
            writer.name(key = key)
            valueSerializer.serialize(writer = writer, value = entryValue)
        }
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): Map<String, V> =
        jsonMap.deserialize(reader = reader)

    override fun deserialize(reader: GhostJsonStringReader): Map<String, V> =
        jsonMap.deserialize(reader = reader)

    override fun deserialize(reader: GhostYamlFlatReader): Map<String, V> =
        reader.readMap(keyParser = { reader.nextKey()!! }) { valueSerializer.deserialize(reader = reader) }
}
