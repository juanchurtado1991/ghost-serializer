package com.ghost.serialization.yaml

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

internal object YamlWidgetSerializer :
    AbstractGhostSerializer<YamlWidget>(),
    GhostYamlSerializer<YamlWidget> {

    override val typeName: String = "YamlWidget"

    override fun serialize(
        writer: GhostJsonWriter,
        value: YamlWidget
    ) = Unit

    override fun deserialize(reader: GhostJsonReader): YamlWidget =
        YamlWidget(code = "", qty = 0)

    override fun deserialize(reader: GhostJsonStringReader): YamlWidget =
        YamlWidget(code = "", qty = 0)

    override fun serialize(writer: GhostYamlWriter, value: YamlWidget) {
        writer.beginObject()
        writer.name(key = "code")
        writer.value(value.code)
        writer.name(key = "qty")
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
        return YamlWidget(code = code, qty = qty)
    }
}
