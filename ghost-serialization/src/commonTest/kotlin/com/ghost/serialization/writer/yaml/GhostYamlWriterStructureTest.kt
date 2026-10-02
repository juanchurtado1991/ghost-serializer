@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [GhostYamlWriter] edge cases: structure, depth protection, empty collections, and reset/reuse. */
class GhostYamlWriterStructureTest {

    // ── STRUCTURE ─────────────────────────────────────────────────

    @Test
    fun writesEmptyObject() {
        // Was "" (a dangling scope with no bytes at all, parsed back as null) until the
        // empty-collection fix — see the "F. EMPTY COLLECTIONS" section below.
        assertEquals(
            expected = "{}",
            actual = yamlWriterToString { w -> w.beginObject().endObject() }.trim()
        )
    }

    @Test
    fun writesEmptyArray() {
        assertEquals(
            expected = "[]",
            actual = yamlWriterToString { w -> w.beginArray().endArray() }.trim()
        )
    }

    @Test
    fun writesArrayWithMultipleValues() {
        val yaml = yamlWriterToString { w ->
            w.beginObject().name(key = "items")
            w.beginArray()
            w.value(1).value(2).value(3)
            w.endArray()
            w.endObject()
        }
        assertTrue(
            actual = yaml.contains("- 1") && yaml.contains("- 2") && yaml.contains("- 3"),
            message = yaml
        )
    }

    @Test
    fun writesNestedObjects() {
        val yaml = yamlWriterToString { w ->
            w.beginObject()
            w.name(key = "outer")
            w.beginObject()
            w.name(key = "inner")
            w.value("deep")
            w.endObject()
            w.endObject()
        }
        assertTrue(
            actual = yaml.contains("outer:") && yaml.contains("inner:") && yaml.contains("deep"),
            message = yaml
        )
    }

    @Test
    fun writesMultipleFieldsWithNewlines() {
        val yaml = yamlWriterToString { w ->
            w.beginObject().name(key = "a").value(1).name(key = "b").value(2).name(key = "c").value(3).endObject()
        }
        assertTrue(
            actual = yaml.contains("a:") && yaml.contains("b:") && yaml.contains("c:"),
            message = yaml
        )
    }

    // ── DEPTH PROTECTION ──────────────────────────────────────────

    @Test
    fun writerRespectsMaxDepth() {
        assertFailsWith<GhostYamlException> {
            val byteWriter = FlatByteArrayWriter()
            val writer = GhostYamlWriter(byteWriter)
            repeat(65) { writer.beginObject().name(key = "a") }
        }
    }

    @Test
    fun writerFillsExactlyMaxDepthWithoutThrowing() {
        // The 64th beginObject() (currentDepth 0..63) must succeed and stay in bounds;
        // only the 65th (currentDepth == MAX_DEPTH) should throw. Regression test for the
        // off-by-one that undersized the contexts/itemCounts arrays by one element.
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostYamlWriter(byteWriter)
        repeat(64) { writer.beginObject().name(key = "a") }
        writer.value(1)
    }

    // ── EMPTY COLLECTIONS ─────────────────────────────────────────

    @Test
    fun emptyNestedObjectFieldRoundTripsAsEmptyMap() {
        val yaml = yamlWriterToString { w ->
            w.beginObject()
            w.name(key = "meta")
            w.beginObject()
            w.endObject()
            w.endObject()
        }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(
            expected = emptyMap<String, Any?>(),
            actual = result["meta"]
        )
    }

    @Test
    fun emptyNestedArrayFieldRoundTripsAsEmptyList() {
        val yaml = yamlWriterToString { w ->
            w.beginObject()
            w.name(key = "tags")
            w.beginArray()
            w.endArray()
            w.endObject()
        }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(
            expected = emptyList<Any?>(),
            actual = result["tags"]
        )
    }

    @Test
    fun emptyArrayItemRoundTripsAsEmptyList() {
        val yaml = yamlWriterToString { w ->
            w.beginArray()
            w.beginArray()
            w.endArray()
            w.value(1)
            w.endArray()
        }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val result = reader.readDocument() as List<*>
        assertEquals(
            expected = listOf(emptyList<Any?>(), 1L),
            actual = result
        )
    }

    @Test
    fun emptyObjectItemRoundTripsAsEmptyMap() {
        val yaml = yamlWriterToString { w ->
            w.beginArray()
            w.beginObject()
            w.endObject()
            w.endArray()
        }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val result = reader.readDocument() as List<*>
        assertEquals(
            expected = listOf(emptyMap<String, Any?>()),
            actual = result
        )
    }

    @Test
    fun rootEmptyObjectRoundTripsAsEmptyMap() {
        val yaml = yamlWriterToString { w -> w.beginObject().endObject() }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        assertEquals(
            expected = emptyMap<String, Any?>(),
            actual = reader.readDocument()
        )
    }

    @Test
    fun rootEmptyArrayRoundTripsAsEmptyList() {
        val yaml = yamlWriterToString { w -> w.beginArray().endArray() }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        assertEquals(
            expected = emptyList<Any?>(),
            actual = reader.readDocument()
        )
    }

    @Test
    fun emptyObjectFieldFollowedBySiblingStaysWellFormed() {
        val yaml = yamlWriterToString { w ->
            w.beginObject()
            w.name(key = "meta")
            w.beginObject()
            w.endObject()
            w.name(key = "count")
            w.value(2)
            w.endObject()
        }
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val result = reader.readDocument() as Map<*, *>
        assertEquals(
            expected = emptyMap<String, Any?>(),
            actual = result["meta"]
        )
        assertEquals(
            expected = 2L,
            actual = result["count"]
        )
    }

    @Test
    fun nameOutsideObjectScopeThrows() {
        assertFailsWith<GhostYamlException> {
            yamlWriterToString { w -> w.name(key = "orphan").value(1) }
        }
    }

    // ── RESET / REUSE ─────────────────────────────────────────────

    @Test
    fun resetAllowsWriterReuseAfterBufferReset() {
        val byteWriter = FlatByteArrayWriter()
        val writer = GhostYamlWriter(byteWriter)

        writer.beginObject().name(key = "a").value(1).endObject()
        assertTrue(actual = byteWriter.toStringUtf8().contains("a:"))

        writer.reset()
        byteWriter.reset()
        writer.beginObject().name(key = "b").value(2).endObject()
        val second = byteWriter.toStringUtf8()
        assertTrue(actual = second.contains("b:"))
        assertTrue(
            actual = !second.contains("a:"),
            message = second
        )
    }
}
