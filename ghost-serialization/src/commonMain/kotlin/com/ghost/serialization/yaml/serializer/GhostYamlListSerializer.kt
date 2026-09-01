package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.GhostYamlConstants as C
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
            C.ERR_YAML_LIST_NEEDS_YAML_ITEM_PREFIX + itemSerializer.typeName
        }
        itemSerializer as GhostYamlSerializer<T>
    }

    private val jsonList = ListSerializer(itemSerializer)

    override val typeName: String
        get() = "List<${itemSerializer.typeName}>"

    override fun serialize(
        writer: GhostJsonWriter,
        value: List<T>
    ) = jsonList.serialize(writer, value)

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: List<T>
    ) = jsonList.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) { yamlItem.serialize(writer, item) }
        writer.endArray()
    }

    override fun deserialize(reader: GhostJsonReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: GhostJsonStringReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): List<T> =
        reader.readList { yamlItem.deserialize(reader) }
}
