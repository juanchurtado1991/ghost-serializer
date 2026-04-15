package com.ghost.serialization.core

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostWriterEdgeCaseTest {

    private fun writerToString(block: (GhostJsonWriter) -> Unit): String {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        block(writer)
        return buffer.readUtf8()
    }

    // ── A. PRIMITIVE OUTPUT ──────────────────────────────────────────

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

    // ── B. STRING ESCAPING ───────────────────────────────────────────

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
    fun escapesBackspaceAndFormFeed() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("\b\u000C").endObject()
        }
        assertEquals("{\"v\":\"\\b\\f\"}", json)
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
    fun writesEmojiDirectly() {
        val json = writerToString { w ->
            w.beginObject().name("v").value("🚀🔥").endObject()
        }
        assertEquals("{\"v\":\"🚀🔥\"}", json)
    }

    // ── C. STRUCTURE ─────────────────────────────────────────────────

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

    // ── D. DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        assertThrowsGhostException {
            repeat(150) { writer.beginObject().name("a") }
        }
    }

    // ── E. ZERO & NEGATIVE VALUES ────────────────────────────────────

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
    fun writesNegativeLong() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(-1L).endObject()
        }
        assertEquals("{\"v\":-1}", json)
    }

    @Test
    fun writesNegativeDouble() {
        val json = writerToString { w ->
            w.beginObject().name("v").value(-0.5).endObject()
        }
        assertEquals("{\"v\":-0.5}", json)
    }

    @Test
    fun writesZeroDouble() {
        val json = writerToString { w -> w.beginObject().name("v").value(0.0).endObject() }
        assertEquals("{\"v\":0.0}", json)
    }

    // ── F. COMPLEX STRUCTURES ────────────────────────────────────────

    @Test
    fun writesObjectInsideArray() {
        val json = writerToString { w ->
            w.beginArray()
                .beginObject().name("id").value(1).endObject()
                .beginObject().name("id").value(2).endObject()
            .endArray()
        }
        assertEquals("[{\"id\":1},{\"id\":2}]", json)
    }

    @Test
    fun writesArrayOfArrays() {
        val json = writerToString { w ->
            w.beginArray()
                .beginArray().value(1).value(2).endArray()
                .beginArray().value(3).value(4).endArray()
            .endArray()
        }
        assertEquals("[[1,2],[3,4]]", json)
    }

    @Test
    fun writesMultipleNullsInObject() {
        val json = writerToString { w ->
            w.beginObject()
                .name("a").nullValue()
                .name("b").nullValue()
                .name("c").nullValue()
            .endObject()
        }
        assertEquals("{\"a\":null,\"b\":null,\"c\":null}", json)
    }

    @Test
    fun writesNullInterleavedWithValues() {
        val json = writerToString { w ->
            w.beginObject()
                .name("a").value(1)
                .name("b").nullValue()
                .name("c").value("text")
                .name("d").nullValue()
                .name("e").value(true)
            .endObject()
        }
        assertEquals("{\"a\":1,\"b\":null,\"c\":\"text\",\"d\":null,\"e\":true}", json)
    }

    @Test
    fun writesDeeplyNestedStructure() {
        val json = writerToString { w ->
            w.beginObject()
                .name("l1").beginObject()
                .name("l2").beginObject()
                .name("l3").beginObject()
                .name("leaf").value("deep")
                .endObject()
                .endObject()
                .endObject()
            .endObject()
        }
        assertEquals(
            "{\"l1\":{\"l2\":{\"l3\":{\"leaf\":\"deep\"}}}}",
            json
        )
    }

    // ── G. FIELD NAME ESCAPING ───────────────────────────────────────

    @Test
    fun escapesQuotesInFieldName() {
        val json = writerToString { w ->
            w.beginObject().name("say\"hi").value(1).endObject()
        }
        assertEquals("{\"say\\\"hi\":1}", json)
    }

    @Test
    fun escapesBackslashInFieldName() {
        val json = writerToString { w ->
            w.beginObject().name("path\\to").value(1).endObject()
        }
        assertEquals("{\"path\\\\to\":1}", json)
    }

    @Test
    fun writesLongString() {
        val longStr = "x".repeat(10_000)
        val json = writerToString { w ->
            w.beginObject().name("v").value(longStr).endObject()
        }
        assertEquals("{\"v\":\"$longStr\"}", json)
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
