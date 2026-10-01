@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8

/** [GhostYamlWriter] edge cases: primitive output and fused writeField overloads. */
class GhostYamlWriterPrimitiveTest {

    // ── PRIMITIVE OUTPUT ──────────────────────────────────────────

    @Test
    fun writesSingleDigitPositiveInt() {
        assertEquals(
            expected = "v: 7",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").value(7).endObject() }.trim()
        )
    }

    @Test
    fun writesSingleDigitNegativeInt() {
        assertEquals(
            expected = "v: -7",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").value(-7).endObject() }.trim()
        )
    }

    @Test
    fun writesMultiDigitInt() {
        assertEquals(
            expected = "v: 12345",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").value(12345).endObject() }.trim()
        )
    }

    @Test
    fun writesIntMinValue() {
        assertEquals(
            expected = "v: ${Int.MIN_VALUE}",
            actual = yamlWriterToString { w ->
                w.beginObject().name(key = "v").value(Int.MIN_VALUE).endObject()
            }.trim()
        )
    }

    @Test
    fun writesLongMaxValue() {
        assertEquals(
            expected = "v: ${Long.MAX_VALUE}",
            actual = yamlWriterToString { w ->
                w.beginObject().name(key = "v").value(Long.MAX_VALUE).endObject()
            }.trim()
        )
    }

    @Test
    fun writesLongMinValue() {
        assertEquals(
            expected = "v: ${Long.MIN_VALUE}",
            actual = yamlWriterToString { w ->
                w.beginObject().name(key = "v").value(Long.MIN_VALUE).endObject()
            }.trim()
        )
    }

    @Test
    fun writesIntMinValueAsLong() {
        assertEquals(
            expected = "v: ${Int.MIN_VALUE}",
            actual = yamlWriterToString { w ->
                w.beginObject().name(key = "v").value(Int.MIN_VALUE.toLong()).endObject()
            }.trim()
        )
    }

    @Test
    fun writesDoubleValue() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(3.14).endObject() }
        assertTrue(
            actual = yaml.contains("3.14"),
            message = yaml
        )
    }

    @Test
    fun writesWholeNumberDouble() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(5.0).endObject() }
        assertTrue(
            actual = yaml.contains("5.0") || yaml.contains("5"),
            message = yaml
        )
    }

    @Test
    fun writesLargeDoubleBeyondSafeIntegerRange() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(1e20).endObject() }
        val parsed = yamlRoundTripScalar(key = "v") { it.value(1e20) }
        assertEquals(
            expected = 1e20,
            actual = (parsed as Number).toDouble()
        )
    }

    @Test
    fun writesNegativeZeroDouble() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(-0.0).endObject() }
        assertTrue(
            actual = yaml.contains("-0.0") || yaml.contains("0.0"),
            message = yaml
        )
    }

    @Test
    fun writesFloatValue() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(2.5f).endObject() }
        assertTrue(
            actual = yaml.contains("2.5"),
            message = yaml
        )
    }

    @Test
    fun writesWholeNumberFloat() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(4.0f).endObject() }
        assertTrue(
            actual = yaml.contains("4.0") || yaml.contains("4"),
            message = yaml
        )
    }

    @Test
    fun writesNegativeZeroFloat() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(-0.0f).endObject() }
        assertTrue(
            actual = yaml.contains("-0.0") || yaml.contains("0.0"),
            message = yaml
        )
    }

    @Test
    fun writesNaNAsStringLiteral() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value(Double.NaN).endObject() }
        assertTrue(
            actual = yaml.contains("NaN"),
            message = yaml
        )
    }

    @Test
    fun writesInfinityAsStringLiteral() {
        val yaml = yamlWriterToString { w ->
            w.beginObject().name(key = "v").value(Double.POSITIVE_INFINITY).endObject()
        }
        assertTrue(
            actual = yaml.contains("Infinity"),
            message = yaml
        )
    }

    @Test
    fun writesBooleanTrue() {
        assertEquals(
            expected = "v: true",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").value(true).endObject() }.trim()
        )
    }

    @Test
    fun writesBooleanFalse() {
        assertEquals(
            expected = "v: false",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").value(false).endObject() }.trim()
        )
    }

    @Test
    fun writesNull() {
        assertEquals(
            expected = "v: null",
            actual = yamlWriterToString { w -> w.beginObject().name(key = "v").nullValue().endObject() }.trim()
        )
    }

    @Test
    fun writesCharValue() {
        val yaml = yamlWriterToString { w -> w.beginObject().name(key = "v").value('x').endObject() }
        assertTrue(
            actual = yaml.contains("\"x\""),
            message = yaml
        )
    }

    @Test
    fun writesULongBeyondLongMaxQuoted() {
        val yaml =
            yamlWriterToString { w -> w.beginObject().name(key = "v").value(ULong.MAX_VALUE).endObject() }
        assertTrue(
            actual = yaml.contains("\"18446744073709551615\""),
            message = yaml
        )
    }

    // ── FUSED writeField(header, value) OVERLOADS ──────────────────

    @Test
    fun writeFieldFusesNameAndIntValue() {
        val header = "\"id\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeField(header = header, value = 42).endObject() }
        assertTrue(
            actual = yaml.contains("id:") && yaml.contains("42"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndLongValue() {
        val header = "\"id\":".encodeUtf8()
        val yaml =
            yamlWriterToString { w -> w.beginObject().writeField(header = header, value = Long.MAX_VALUE).endObject() }
        assertTrue(
            actual = yaml.contains("${Long.MAX_VALUE}"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndStringValue() {
        val header = "\"name\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeField(header = header, value = "ghost").endObject() }
        assertTrue(
            actual = yaml.contains("ghost"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndBooleanValue() {
        val header = "\"active\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeField(header = header, value = true).endObject() }
        assertTrue(
            actual = yaml.contains("true"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndDoubleValue() {
        val header = "\"score\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeField(header = header, value = 3.5).endObject() }
        assertTrue(
            actual = yaml.contains("3.5"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndFloatValue() {
        val header = "\"score\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeField(header = header, value = 1.5f).endObject() }
        assertTrue(
            actual = yaml.contains("1.5"),
            message = yaml
        )
    }

    @Test
    fun writeFieldFusesNameAndULongValue() {
        val header = "\"shard\":".encodeUtf8()
        val yaml =
            yamlWriterToString { w -> w.beginObject().writeField(header = header, value = ULong.MAX_VALUE).endObject() }
        assertTrue(
            actual = yaml.contains("18446744073709551615"),
            message = yaml
        )
    }

    @Test
    fun writeNameRawDelegatesToByteStringName() {
        val header = "\"id\":".encodeUtf8()
        val yaml = yamlWriterToString { w -> w.beginObject().writeNameRaw(header = header).value(1).endObject() }
        assertTrue(
            actual = yaml.contains("id:") && yaml.contains("1"),
            message = yaml
        )
    }
}
