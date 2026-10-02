package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML set body serializer — used when resolving `Set<T>`. [itemSerializer]'s type is
 * constrained to implement both [GhostSerializer] and [GhostYamlSerializer] at compile time, so
 * no runtime check/cast is needed to use it on either channel.
 */
class GhostYamlSetSerializer<T, S>(private val itemSerializer: S) :
    AbstractGhostSerializer<Set<T>>(),
    GhostYamlSerializer<Set<T>> where S : GhostSerializer<T>, S : GhostYamlSerializer<T> {

    private val jsonSet = SetSerializer(itemSerializer = itemSerializer)

    override val typeName: String
        get() = "Set<${itemSerializer.typeName}>"

    override fun serialize(writer: GhostJsonWriter, value: Set<T>) =
        jsonSet.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostJsonStringWriter, value: Set<T>) =
        jsonSet.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostYamlWriter, value: Set<T>) {
        writer.beginArray()
        for (item in value) {
            itemSerializer.serialize(writer = writer, value = item)
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): Set<T> = jsonSet.deserialize(reader = reader)

    override fun deserialize(reader: GhostJsonStringReader): Set<T> =
        jsonSet.deserialize(reader = reader)

    override fun deserialize(reader: GhostYamlFlatReader): Set<T> =
        reader.readSet { itemSerializer.deserialize(reader = reader) }
}
