package com.ghost.serialization.yaml

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlBooleanArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlDoubleArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlFloatArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlIntArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlLongArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private data class YamlWidget(val code: String, val qty: Int)

private object YamlWidgetSerializer :
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

class GhostYamlCollectionSerializersTest {

    @Test
    fun listSerializer_rejectsNonYamlItemSerializer() {
        val jsonOnly = object : GhostSerializer<YamlWidget> {
            override val typeName: String = "jsonOnly"
            override fun serialize(
                writer: com.ghost.serialization.writer.bytes.GhostJsonWriter,
                value: YamlWidget,
            ) = Unit

            override fun serialize(
                writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter,
                value: YamlWidget,
            ) = Unit

            override fun deserialize(
                reader: com.ghost.serialization.parser.streaming.GhostJsonReader,
            ): YamlWidget = YamlWidget("", 0)

            override fun deserialize(
                reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader,
            ): YamlWidget = YamlWidget("", 0)

            override fun deserialize(
                reader: com.ghost.serialization.parser.strings.GhostJsonStringReader,
            ): YamlWidget = YamlWidget("", 0)
        }

        assertFailsWith<IllegalArgumentException> {
            GhostYamlListSerializer(jsonOnly)
        }
    }

    @Test
    fun listSerializer_roundTripsEmptyAndMultiElementFlowSequence() {
        val serializer = GhostYamlListSerializer(YamlWidgetSerializer)

        val emptyYaml = """
            []
        """.trimIndent()
        assertEquals(
            emptyList(),
            serializer.deserialize(GhostYamlFlatReader(emptyYaml.encodeToByteArray()))
        )

        val yaml = """
            - code: alpha
              qty: 1
            - code: beta
              qty: 2
        """.trimIndent()
        val parsed = serializer.deserialize(GhostYamlFlatReader(yaml.encodeToByteArray()))
        assertEquals(
            listOf(YamlWidget("alpha", 1), YamlWidget("beta", 2)),
            parsed,
        )

        val bytes = ghostYamlInternalUseFlatWriter { writer ->
            serializer.serialize(writer, parsed)
            writer.buffer.toByteArray()
        }
        val roundTrip = serializer.deserialize(GhostYamlFlatReader(bytes))
        assertEquals(parsed, roundTrip)
        assertTrue(bytes.decodeToString().contains("alpha"))
    }

    @Test
    fun mapSerializer_roundTripsStringKeysAndEmptyMap() {
        val serializer = GhostYamlMapSerializer(YamlWidgetSerializer)

        val emptyYaml = "{}\n"
        assertEquals(
            emptyMap(),
            serializer.deserialize(GhostYamlFlatReader(emptyYaml.encodeToByteArray()))
        )

        val yaml = """
            alpha:
              code: alpha
              qty: 10
            beta:
              code: beta
              qty: 20
        """.trimIndent()
        val expected = mapOf(
            "alpha" to YamlWidget("alpha", 10),
            "beta" to YamlWidget("beta", 20),
        )
        val parsed = serializer.deserialize(GhostYamlFlatReader(yaml.encodeToByteArray()))
        assertEquals(expected, parsed)
    }

    @Test
    fun primitiveArraySerializers_roundTripAllScalarKinds() {
        val intYaml = """
            [1, 2, 3]
        """.trimIndent()
        assertContentEquals(
            intArrayOf(1, 2, 3),
            GhostYamlIntArraySerializer.deserialize(GhostYamlFlatReader(intYaml.encodeToByteArray())),
        )

        val longYaml = """
            [100, 200]
        """.trimIndent()
        assertContentEquals(
            longArrayOf(100L, 200L),
            GhostYamlLongArraySerializer.deserialize(GhostYamlFlatReader(longYaml.encodeToByteArray())),
        )

        val floatYaml = """
            [1.5, 2.25]
        """.trimIndent()
        assertContentEquals(
            floatArrayOf(1.5f, 2.25f),
            GhostYamlFloatArraySerializer.deserialize(GhostYamlFlatReader(floatYaml.encodeToByteArray())),
        )

        val doubleYaml = """
            [3.14, 2.718]
        """.trimIndent()
        assertContentEquals(
            doubleArrayOf(3.14, 2.718),
            GhostYamlDoubleArraySerializer.deserialize(GhostYamlFlatReader(doubleYaml.encodeToByteArray())),
        )

        val booleanYaml = """
            [true, false, true]
        """.trimIndent()
        assertContentEquals(
            booleanArrayOf(true, false, true),
            GhostYamlBooleanArraySerializer.deserialize(GhostYamlFlatReader(booleanYaml.encodeToByteArray())),
        )

        val source = intArrayOf(7, 8, 9)
        val bytes = ghostYamlInternalUseFlatWriter { writer ->
            GhostYamlIntArraySerializer.serialize(writer, source)
            writer.buffer.toByteArray()
        }
        assertContentEquals(
            source,
            GhostYamlIntArraySerializer.deserialize(GhostYamlFlatReader(bytes)),
        )
    }
}
