@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.writer.FlatCharArrayWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostStringWriterTest {

    private fun writerToString(block: (GhostJsonStringWriter) -> Unit): String {
        val charWriter = FlatCharArrayWriter()
        val writer = GhostJsonStringWriter(charWriter)
        block(writer)
        return charWriter.toString()
    }

    @Test
    fun writesIntValue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(42).endObject()
        }
        assertEquals("{\"v\":42}", json)
    }

    @Test
    fun writesLongMaxValue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(Long.MAX_VALUE).endObject()
        }
        assertEquals("{\"v\":${Long.MAX_VALUE}}", json)
    }

    @Test
    fun writesLongMinValue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(Long.MIN_VALUE).endObject()
        }
        assertEquals("{\"v\":${Long.MIN_VALUE}}", json)
    }

    @Test
    fun writesDoubleValue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(3.14).endObject()
        }
        assertEquals("{\"v\":3.14}", json)
    }

    @Test
    fun writesFloatValue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(2.5f).endObject()
        }
        assertEquals("{\"v\":2.5}", json)
    }

    @Test
    fun writesBooleanTrue() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(true).endObject()
        }
        assertEquals("{\"v\":true}", json)
    }

    @Test
    fun writesBooleanFalse() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(false).endObject()
        }
        assertEquals("{\"v\":false}", json)
    }

    @Test
    fun writesNull() {
        val json = writerToString { w ->
            w.beginObject().name("v").nullValue().endObject()
        }
        assertEquals("{\"v\":null}", json)
    }

    @Test
    fun escapesQuotesInString() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("say \"hello\"").endObject()
        }
        assertEquals("{\"v\":\"say \\\"hello\\\"\"}", json)
    }

    @Test
    fun escapesBackslash() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("path\\to").endObject()
        }
        assertEquals("{\"v\":\"path\\\\to\"}", json)
    }

    @Test
    fun escapesControlCharacters() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("a\nb\tc\rd").endObject()
        }
        assertEquals("{\"v\":\"a\\nb\\tc\\rd\"}", json)
    }

    @Test
    fun writesEmptyString() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("").endObject()
        }
        assertEquals("{\"v\":\"\"}", json)
    }

    @Test
    fun writesUnicodeDirectly() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("漢字").endObject()
        }
        assertEquals("{\"v\":\"漢字\"}", json)
    }

    @Test
    fun writesEmptyObject() {
        val json = writerToString { w -> w.beginObject().endObject() }
        assertEquals("{}", json)
    }

    @Test
    fun writesEmptyArray() {
        val json = writerToString { w -> w.beginArray().endArray() }
        assertEquals("[]", json)
    }

    @Test
    fun writesArrayWithMultipleValues() {
        val json = writerToString { w ->
            w.beginArray().value(1).value(2).value(3).endArray()
        }
        assertEquals("[1,2,3]", json)
    }

    @Test
    fun writesNestedObjects() {
        val json = writerToString { w ->
            w.beginObject()
                .name("outer")
                .beginObject()
                .name("inner").value("deep")
                .endObject()
                .endObject()
        }
        assertEquals("{\"outer\":{\"inner\":\"deep\"}}", json)
    }

    @Test
    fun writesArrayInsideObject() {
        val json = writerToString { w ->
            w.beginObject()
                .name("items")
                .beginArray()
                .value("a")
                .value("b")
                .endArray()
                .endObject()
        }
        assertEquals("{\"items\":[\"a\",\"b\"]}", json)
    }

    @Test
    fun writesMultipleFieldsWithCommas() {
        val json = writerToString { w ->
            w.beginObject()
                .name("a").value(1)
                .name("b").value(2)
                .name("c").value(3)
                .endObject()
        }
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", json)
    }

    @Test
    fun writerRespectsMaxDepth() {
        val charWriter = FlatCharArrayWriter()
        val writer = GhostJsonStringWriter(charWriter)
        assertThrowsGhostException {
            repeat(300) { writer.beginObject().name("a") }
        }
    }

    @Test
    fun writesZeroInt() {
        val json = writerToString { w -> w.beginObject().name("v").value(0).endObject() }
        assertEquals("{\"v\":0}", json)
    }

    @Test
    fun writesNegativeInt() {
        val json = writerToString { w -> w.beginObject().name("v").value(-999).endObject() }
        assertEquals("{\"v\":-999}", json)
    }

    @Test
    fun writesWithZeroCapacityWriter() {
        val writer = FlatCharArrayWriter(0)
        writer.writeChar('A'.code)
        assertEquals("A", writer.toString())
    }

    private inline fun assertThrowsGhostException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected GhostJsonException to be thrown")
        } catch (e: GhostJsonException) {
            // Expected
        }
    }
}
