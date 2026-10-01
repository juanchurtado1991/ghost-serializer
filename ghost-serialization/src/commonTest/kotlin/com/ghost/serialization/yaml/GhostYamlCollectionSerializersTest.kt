package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
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
import kotlin.test.assertTrue

/**
 * [GhostYamlListSerializer]/[GhostYamlMapSerializer] no longer accept a JSON-only item/value
 * serializer at all — the constructor's `where S : GhostSerializer<T>, S : GhostYamlSerializer<T>`
 * bound makes that a compile error, not a runtime [IllegalArgumentException] (dropped the old
 * `listSerializer_rejectsNonYamlItemSerializer` test that exercised the runtime check this
 * replaced).
 */
class GhostYamlCollectionSerializersTest {

    @Test
    fun listSerializer_roundTripsEmptyAndMultiElementFlowSequence() {
        val serializer = GhostYamlListSerializer(itemSerializer = YamlWidgetSerializer)

        val emptyYaml = """
            []
        """.trimIndent()
        assertEquals(
            expected = emptyList(),
            actual = serializer.deserialize(GhostYamlFlatReader(rawData = emptyYaml.encodeToByteArray()))
        )

        val yaml = """
            - code: alpha
              qty: 1
            - code: beta
              qty: 2
        """.trimIndent()
        val parsed = serializer.deserialize(GhostYamlFlatReader(rawData = yaml.encodeToByteArray()))
        assertEquals(
            expected = listOf(YamlWidget(code = "alpha", qty = 1), YamlWidget(code = "beta", qty = 2)),
            actual = parsed
        )

        val bytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            serializer.serialize(writer, parsed)
            buffer.toByteArray()
        }
        val roundTrip = serializer.deserialize(GhostYamlFlatReader(rawData = bytes))
        assertEquals(
            expected = parsed,
            actual = roundTrip
        )
        assertTrue(actual = bytes.decodeToString().contains("alpha"))
    }

    @Test
    fun mapSerializer_roundTripsStringKeysAndEmptyMap() {
        val serializer = GhostYamlMapSerializer(valueSerializer = YamlWidgetSerializer)

        val emptyYaml = "{}\n"
        assertEquals(
            expected = emptyMap(),
            actual = serializer.deserialize(GhostYamlFlatReader(rawData = emptyYaml.encodeToByteArray()))
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
            "alpha" to YamlWidget(code = "alpha", qty = 10),
            "beta" to YamlWidget(code = "beta", qty = 20),
        )
        val parsed = serializer.deserialize(GhostYamlFlatReader(rawData = yaml.encodeToByteArray()))
        assertEquals(
            expected = expected,
            actual = parsed
        )
    }

    @Test
    fun primitiveArraySerializers_roundTripAllScalarKinds() {
        val intYaml = """
            [1, 2, 3]
        """.trimIndent()
        assertContentEquals(
            expected = intArrayOf(1, 2, 3),
            actual = GhostYamlIntArraySerializer.deserialize(GhostYamlFlatReader(rawData = intYaml.encodeToByteArray()))
        )

        val longYaml = """
            [100, 200]
        """.trimIndent()
        assertContentEquals(
            expected = longArrayOf(100L, 200L),
            actual = GhostYamlLongArraySerializer.deserialize(
                GhostYamlFlatReader(rawData = longYaml.encodeToByteArray())
            )
        )

        val floatYaml = """
            [1.5, 2.25]
        """.trimIndent()
        assertContentEquals(
            expected = floatArrayOf(1.5f, 2.25f),
            actual = GhostYamlFloatArraySerializer.deserialize(
                GhostYamlFlatReader(rawData = floatYaml.encodeToByteArray())
            )
        )

        val doubleYaml = """
            [3.14, 2.718]
        """.trimIndent()
        assertContentEquals(
            expected = doubleArrayOf(3.14, 2.718),
            actual = GhostYamlDoubleArraySerializer.deserialize(
                GhostYamlFlatReader(rawData = doubleYaml.encodeToByteArray())
            )
        )

        val booleanYaml = """
            [true, false, true]
        """.trimIndent()
        assertContentEquals(
            expected = booleanArrayOf(true, false, true),
            actual = GhostYamlBooleanArraySerializer.deserialize(
                GhostYamlFlatReader(rawData = booleanYaml.encodeToByteArray())
            )
        )

        val source = intArrayOf(7, 8, 9)
        val bytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            GhostYamlIntArraySerializer.serialize(writer, source)
            buffer.toByteArray()
        }
        assertContentEquals(
            expected = source,
            actual = GhostYamlIntArraySerializer.deserialize(GhostYamlFlatReader(rawData = bytes))
        )
    }
}
