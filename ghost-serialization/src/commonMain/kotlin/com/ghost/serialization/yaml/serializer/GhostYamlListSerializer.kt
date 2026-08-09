package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML list body serializer — used by Retrofit/Ktor YAML adapters for `List<T>` endpoints when
 * [itemSerializer] also implements [GhostYamlSerializer].
 */
class GhostYamlListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>,
) : GhostSerializer<List<T>>, GhostYamlSerializer<List<T>> {

    @Suppress("UNCHECKED_CAST")
    private val yamlItem: GhostYamlSerializer<T> = run {
        require(itemSerializer is GhostYamlSerializer<*>) {
            "GhostYamlListSerializer requires a GhostYamlSerializer item serializer, got ${itemSerializer.typeName}"
        }
        itemSerializer as GhostYamlSerializer<T>
    }

    private val jsonList = ListSerializer(itemSerializer)

    override val typeName: String
        get() = "List<${itemSerializer.typeName}>"

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: List<T>
    ) =
        jsonList.serialize(writer, value)

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
        value: List<T>
    ) =
        jsonList.serialize(writer, value)

    override fun serialize(
        writer: com.ghost.serialization.writer.strings.GhostJsonStringWriter,
        value: List<T>
    ) =
        jsonList.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) {
            yamlItem.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) {
            yamlItem.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): List<T> =
        reader.readList { yamlItem.deserialize(reader) }
}
