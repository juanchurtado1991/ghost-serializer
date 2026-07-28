package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.strings.beginObject

class GhostYamlMultiDocumentTest {

    private data class Widget(val id: Int, val label: String)

    private object WidgetSerializer : GhostYamlSerializer<Widget> {
        override fun serialize(writer: com.ghost.serialization.writer.yaml.GhostYamlWriter, value: Widget) = Unit

        override fun serialize(writer: GhostYamlFlatWriter, value: Widget) {
            writer.beginObject()
            writer.name("id").value(value.id)
            writer.name("label").value(value.label)
            writer.endObject()
        }

        override fun deserialize(reader: GhostYamlFlatReader): Widget {
            reader.beginObject()
            var id = 0
            var label = ""
            while (true) {
                when (reader.selectNameAndConsume(com.ghost.serialization.parser.common.JsonReaderOptions.of("id", "label"))) {
                    0 -> id = reader.nextInt()
                    1 -> label = reader.nextString()
                    -1 -> break
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return Widget(id, label)
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

        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val parsed = reader.readAllDocuments { docReader -> WidgetSerializer.deserialize(docReader) }

        assertEquals(listOf(Widget(1, "first"), Widget(2, "second")), parsed)
    }

    @Test
    fun readDocument_preservesQuotedULongAsString() {
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()
        val map = GhostYamlFlatReader(yaml.encodeToByteArray()).readDocument() as Map<*, *>
        assertEquals("18446744073709551615", map["shard_id"])
    }

    @Test
    fun nextULong_acceptsQuotedMaxValueForSnakeCaseKey() {
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(com.ghost.serialization.parser.common.JsonReaderOptions.of("shard_id"))
        assertEquals(ULong.MAX_VALUE, reader.nextULong())
    }

    @Test
    fun nextULong_acceptsQuotedMaxValueOnProtoFlatReader() {
        val yaml = """value: "18446744073709551615""""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(com.ghost.serialization.parser.common.JsonReaderOptions.of("value"))
        assertEquals(ULong.MAX_VALUE, reader.nextULong())
    }

    @Test
    fun nextULong_acceptsBareNumberWithinLongRange() {
        val yaml = """value: 42"""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(com.ghost.serialization.parser.common.JsonReaderOptions.of("value"))
        assertEquals(42uL, reader.nextULong())
    }

    @Test
    fun nextULongOrNull_returnsNullForYamlNull() {
        val yaml = """value: null"""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(com.ghost.serialization.parser.common.JsonReaderOptions.of("value"))
        assertEquals(null, reader.nextULongOrNull())
    }
}
