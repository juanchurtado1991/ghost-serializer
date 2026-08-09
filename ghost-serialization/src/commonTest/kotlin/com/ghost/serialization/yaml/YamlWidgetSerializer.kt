package com.ghost.serialization.yaml

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

internal object YamlWidgetSerializer :
    GhostSerializer<YamlWidget>,
    GhostYamlSerializer<YamlWidget> {
    override val typeName: String = "YamlWidget"

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
        value: YamlWidget
    ) = Unit

    override fun serialize(
        writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
        value: YamlWidget
    ) = Unit

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): YamlWidget =
        YamlWidget("", 0)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): YamlWidget =
        YamlWidget("", 0)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): YamlWidget =
        YamlWidget("", 0)

    override fun serialize(writer: GhostYamlWriter, value: YamlWidget) {
        writer.beginObject()
        writer.name("code")
        writer.value(value.code)
        writer.name("qty")
        writer.value(value.qty)
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: YamlWidget) {
        writer.beginObject()
        writer.name("code")
        writer.value(value.code)
        writer.name("qty")
        writer.value(value.qty)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): YamlWidget {
        var code = ""
        var qty = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextKey()) {
                "code" -> code = reader.nextString()
                "qty" -> qty = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return YamlWidget(code, qty)
    }
}
