@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.writer.strings.FlatCharArrayWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.test.Test
import kotlin.test.assertEquals


class GhostStringWriterTest {

    private fun writerToString(block: (GhostJsonStringWriter) -> Unit): String {
        val charWriter = FlatCharArrayWriter()
        val writer = GhostJsonStringWriter(buffer = charWriter)
        block(writer)
        return charWriter.toString()
    }

    @Test
    fun writesIntValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(42).endObject()
        }
        assertEquals(
            expected = "{\"v\":42}",
            actual = json
        )
    }

    @Test
    fun writesLongMaxValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(Long.MAX_VALUE).endObject()
        }
        assertEquals(
            expected = "{\"v\":${Long.MAX_VALUE}}",
            actual = json
        )
    }

    @Test
    fun writesLongMinValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(Long.MIN_VALUE).endObject()
        }
        assertEquals(
            expected = "{\"v\":${Long.MIN_VALUE}}",
            actual = json
        )
    }

    @Test
    fun writesDoubleValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(3.14).endObject()
        }
        assertEquals(
            expected = "{\"v\":3.14}",
            actual = json
        )
    }

    @Test
    fun writesFloatValue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(2.5f).endObject()
        }
        assertEquals(
            expected = "{\"v\":2.5}",
            actual = json
        )
    }

    @Test
    fun writesBooleanTrue() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(true).endObject()
        }
        assertEquals(
            expected = "{\"v\":true}",
            actual = json
        )
    }

    @Test
    fun writesBooleanFalse() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(false).endObject()
        }
        assertEquals(
            expected = "{\"v\":false}",
            actual = json
        )
    }

    @Test
    fun writesNull() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").nullValue().endObject()
        }
        assertEquals(
            expected = "{\"v\":null}",
            actual = json
        )
    }

    @Test
    fun escapesQuotesInString() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("say \"hello\"").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"say \\\"hello\\\"\"}",
            actual = json
        )
    }

    @Test
    fun escapesBackslash() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("path\\to").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"path\\\\to\"}",
            actual = json
        )
    }

    @Test
    fun escapesControlCharacters() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("a\nb\tc\rd").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"a\\nb\\tc\\rd\"}",
            actual = json
        )
    }

    @Test
    fun writesEmptyString() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"\"}",
            actual = json
        )
    }

    @Test
    fun writesUnicodeDirectly() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("漢字").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"漢字\"}",
            actual = json
        )
    }

    @Test
    fun writesLongUnicodeWithoutEscapesOnFastPath() {
        // Non-ASCII BMP chars used to previously force the escape slow path unnecessarily.
        val value = "漢".repeat(200)
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value(value).endObject()
        }
        assertEquals(
            expected = "{\"v\":\"$value\"}",
            actual = json
        )
    }

    @Test
    fun escapesQuoteInsideUnicodeString() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("漢\"字").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"漢\\\"字\"}",
            actual = json
        )
    }

    @Test
    fun writesSurrogatePairEmojiDirectly() {
        val json = writerToString { w ->
            w.beginObject().name(key = "v").value("😀").endObject()
        }
        assertEquals(
            expected = "{\"v\":\"😀\"}",
            actual = json
        )
    }

    @Test
    fun writesEmptyObject() {
        val json = writerToString { w -> w.beginObject().endObject() }
        assertEquals(
            expected = "{}",
            actual = json
        )
    }

    @Test
    fun writesEmptyArray() {
        val json = writerToString { w -> w.beginArray().endArray() }
        assertEquals(
            expected = "[]",
            actual = json
        )
    }

    @Test
    fun writesArrayWithMultipleValues() {
        val json = writerToString { w ->
            w.beginArray().value(1).value(2).value(3).endArray()
        }
        assertEquals(
            expected = "[1,2,3]",
            actual = json
        )
    }

    @Test
    fun writesNestedObjects() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "outer")
                .beginObject()
                .name(key = "inner").value("deep")
                .endObject()
                .endObject()
        }
        assertEquals(
            expected = "{\"outer\":{\"inner\":\"deep\"}}",
            actual = json
        )
    }

    @Test
    fun writesArrayInsideObject() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "items")
                .beginArray()
                .value("a")
                .value("b")
                .endArray()
                .endObject()
        }
        assertEquals(
            expected = "{\"items\":[\"a\",\"b\"]}",
            actual = json
        )
    }

    @Test
    fun writesMultipleFieldsWithCommas() {
        val json = writerToString { w ->
            w.beginObject()
                .name(key = "a").value(1)
                .name(key = "b").value(2)
                .name(key = "c").value(3)
                .endObject()
        }
        assertEquals(
            expected = "{\"a\":1,\"b\":2,\"c\":3}",
            actual = json
        )
    }

    @Test
    fun writerRespectsMaxDepth() {
        val charWriter = FlatCharArrayWriter()
        val writer = GhostJsonStringWriter(buffer = charWriter)
        assertThrowsGhostException {
            repeat(300) { writer.beginObject().name(key = "a") }
        }
    }

    @Test
    fun writesZeroInt() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(0).endObject() }
        assertEquals(
            expected = "{\"v\":0}",
            actual = json
        )
    }

    @Test
    fun writesNegativeInt() {
        val json = writerToString { w -> w.beginObject().name(key = "v").value(-999).endObject() }
        assertEquals(
            expected = "{\"v\":-999}",
            actual = json
        )
    }

    @Test
    fun writesWithZeroCapacityWriter() {
        val writer = FlatCharArrayWriter(initialCapacity = 0)
        writer.writeChar(charAsInt = 'A'.code)
        assertEquals(
            expected = "A",
            actual = writer.toString()
        )
    }

    private inline fun assertThrowsGhostException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected GhostJsonException to be thrown")
        } catch (e: GhostJsonException) {
        }
    }
}
