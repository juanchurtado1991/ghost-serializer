package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.NullableSerializer
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

/** [NullableSerializer] for a [delegate] that also supports YAML, so `List<T?>` keeps its YAML codec. */
class NullableYamlSerializer<T : Any, S>(
    private val delegate: S
) : NullableSerializer<T>(delegate = delegate), GhostYamlSerializer<T?>
    where S : GhostSerializer<T>, S : GhostYamlSerializer<T> {

    override fun deserialize(reader: GhostYamlFlatReader): T? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return null
        }
        return delegate.deserialize(reader = reader)
    }

    override fun serialize(writer: GhostYamlWriter, value: T?) {
        if (value == null) writer.nullValue() else delegate.serialize(writer = writer, value = value)
    }
}
