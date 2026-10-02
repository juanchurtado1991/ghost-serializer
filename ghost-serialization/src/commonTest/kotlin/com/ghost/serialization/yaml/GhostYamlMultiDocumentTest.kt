package com.ghost.serialization.yaml

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readAllDocuments
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostYamlMultiDocumentTest {

    private data class Widget(val id: Int, val label: String)

    private object WidgetSerializer : GhostYamlSerializer<Widget> {
        override fun serialize(writer: GhostYamlWriter, value: Widget) {
            writer.beginObject()
            writer.name(key = "id").value(value.id)
            writer.name(key = "label").value(value.label)
            writer.endObject()
        }

        override fun deserialize(reader: GhostYamlFlatReader): Widget {
            reader.beginObject()
            var id = 0
            var label = ""
            while (true) {
                when (reader.selectNameAndConsume(
                    JsonReaderOptions.of(
                        "id",
                        "label"
                    )
                )) {
                    0 -> id = reader.nextInt()
                    1 -> label = reader.nextString()
                    -1 -> break
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return Widget(id = id, label = label)
        }
    }

    @Test
    fun readAllDocumentsTyped_deserializesEachDocument() {
        val yaml = """
            id: 1
            label: first
            ---
            id: 2
            label: second
        """.trimIndent()

        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val parsed =
            reader.readAllDocuments { docReader -> WidgetSerializer.deserialize(docReader) }

        assertEquals(
            expected = listOf(Widget(id = 1, label = "first"), Widget(id = 2, label = "second")),
            actual = parsed
        )
    }

    @Test
    fun readDocument_preservesQuotedULongAsString() {
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()
        val map = GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).readDocument() as Map<*, *>
        assertEquals(
            expected = "18446744073709551615",
            actual = map["shard_id"]
        )
    }

    @Test
    fun nextULong_acceptsQuotedMaxValueForSnakeCaseKey() {
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of("shard_id"))
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = reader.nextULong()
        )
    }

    @Test
    fun nextULong_acceptsQuotedMaxValueOnProtoFlatReader() {
        val yaml = """value: "18446744073709551615""""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("value"))
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = reader.nextULong()
        )
    }

    @Test
    fun nextULong_acceptsBareNumberWithinLongRange() {
        val yaml = """value: 42"""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("value"))
        assertEquals(
            expected = 42uL,
            actual = reader.nextULong()
        )
    }

    @Test
    fun nextULongOrNull_returnsNullForYamlNull() {
        val yaml = """value: null"""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(JsonReaderOptions.of("value"))
        assertEquals(
            expected = null,
            actual = reader.nextULongOrNull()
        )
    }
}
