package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/**
 * YAML set body serializer — used when resolving `Set<T>` and [itemSerializer] also implements
 * [GhostYamlSerializer].
 */
class GhostYamlSetSerializer<T>(
    private val itemSerializer: GhostSerializer<T>,
) : GhostSerializer<Set<T>>, GhostYamlSerializer<Set<T>> {

    @Suppress("UNCHECKED_CAST")
    private val yamlItem: GhostYamlSerializer<T> = run {
        require(itemSerializer is GhostYamlSerializer<*>) {
            C.ERR_YAML_SET_NEEDS_YAML_ITEM_PREFIX + itemSerializer.typeName
        }
        itemSerializer as GhostYamlSerializer<T>
    }

    private val jsonSet = SetSerializer(itemSerializer)

    override val typeName: String
        get() = "Set<${itemSerializer.typeName}>"

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: Set<T>
    ) =
        jsonSet.serialize(writer, value)

    override fun serialize(
        writer: com.ghost.serialization.writer.strings.GhostJsonStringWriter,
        value: Set<T>
    ) =
        jsonSet.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: Set<T>) {
        writer.beginArray()
        for (item in value) {
            yamlItem.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): Set<T> =
        jsonSet.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): Set<T> =
        jsonSet.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): Set<T> =
        reader.readSet { yamlItem.deserialize(reader) }
}
