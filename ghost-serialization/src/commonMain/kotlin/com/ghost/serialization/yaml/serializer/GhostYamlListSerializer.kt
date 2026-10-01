package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML list body serializer — used by Retrofit/Ktor YAML adapters for `List<T>` endpoints.
 * [itemSerializer]'s type is constrained to implement both [GhostSerializer] and
 * [GhostYamlSerializer] at compile time, so no runtime check/cast is needed to use it on
 * either channel.
 */
class GhostYamlListSerializer<T, S>(
    private val itemSerializer: S,
) : AbstractGhostSerializer<List<T>>(), GhostYamlSerializer<List<T>> where S : GhostSerializer<T>, S : GhostYamlSerializer<T> {

    private val jsonList = ListSerializer(itemSerializer = itemSerializer)

    override val typeName: String
        get() = "List<${itemSerializer.typeName}>"

    override fun deserialize(reader: GhostJsonReader): List<T> =
        jsonList.deserialize(reader = reader)

    override fun deserialize(reader: GhostJsonStringReader): List<T> =
        jsonList.deserialize(reader = reader)

    override fun deserialize(reader: GhostYamlFlatReader): List<T> =
        reader.readList { itemSerializer.deserialize(reader = reader) }

    override fun serialize(writer: GhostJsonWriter, value: List<T>) =
        jsonList.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostJsonStringWriter, value: List<T>) =
        jsonList.serialize(writer = writer, value = value)

    override fun serialize(writer: GhostYamlWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) {
            itemSerializer.serialize(
                writer = writer,
                value = item
            )
        }
        writer.endArray()
    }
}
