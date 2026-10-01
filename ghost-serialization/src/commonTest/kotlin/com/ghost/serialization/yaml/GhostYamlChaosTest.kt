@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer

/**
 * Mirrors the failure modes covered by `GhostChaosTest` and `GhostStressAuditTest`, for YAML.
 */
class GhostYamlChaosTest {

    private data class PairBox(val left: String, val right: Int)

    private object PairBoxSerializer : GhostYamlSerializer<PairBox> {
        override fun serialize(writer: GhostYamlWriter, value: PairBox) {
            writer.beginObject()
            writer.name(key = "left").value(value.left)
            writer.name(key = "right").value(value.right)
            writer.endObject()
        }

        override fun deserialize(reader: GhostYamlFlatReader): PairBox {
            var left = ""
            var right = 0
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextKey()) {
                    "left" -> left = reader.nextString()
                    "right" -> right = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return PairBox(left = left, right = right)
        }
    }

    @Test
    fun surrogatePairInDoubleQuotedStringRoundTrips() {
        val yaml = """emoji: "😀""""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        assertEquals(
            expected = "😀",
            actual = reader.nextString()
        )
    }

    @Test
    fun malformedSurrogateInEscapeSequenceIsAcceptedOrThrows() {
        val yaml = """v: "\uD83D\u0020""""
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        runCatching { reader.nextString() }.onFailure {
            assertFailsWith<GhostYamlException> { throw it }
        }
    }

    @Test
    fun deepBlockSequenceWithinLimitParses() {
        val yaml = """
            items:
              - one
              - two
              - three
        """.trimIndent()
        val doc = GhostYamlFlatReader(yaml.encodeToByteArray()).readDocument() as Map<*, *>
        val items = doc["items"] as List<*>
        assertEquals(
            expected = listOf("one", "two", "three"),
            actual = items
        )
    }

    @Test
    fun malformedYamlFuzzingThrows() {
        val malformedInputs = listOf(
            "\"escaped\\u123z\"",
            "key: |\n  line\n bad-indent",
        )

        malformedInputs.forEach { input ->
            assertFailsWith<Exception>("Expected failure for: $input") {
                GhostYamlFlatReader(input.encodeToByteArray()).readDocument()
            }
        }
    }

    @Test
    fun flowMappingWithDuplicateCommaIsRejected() {
        // A double comma is an empty entry, which the flow-mapping grammar disallows
        // (yaml-test-suite CTN5); Ghost used to silently skip it instead of rejecting it.
        assertFailsWith<GhostYamlException> {
            GhostYamlFlatReader("{a: 1,, b: 2}".encodeToByteArray()).readDocument()
        }
    }

    @Test
    fun flowSequenceWithTrailingCommaParsesLeniently() {
        val list = GhostYamlFlatReader("[1, 2, ]".encodeToByteArray()).readDocument() as List<*>
        assertEquals(
            expected = listOf(1L, 2L),
            actual = list
        )
    }

    @Test
    fun longKeyPastBufferBoundaryParses() {
        val longKey = "k".repeat(9000)
        val yaml = "$longKey: value"
        val map = GhostYamlFlatReader(yaml.encodeToByteArray()).readDocument() as Map<*, *>
        assertEquals(
            expected = "value",
            actual = map[longKey]
        )
    }

    @Test
    fun longValuePastBufferBoundaryRoundTripsViaWriter() {
        val longValue = "x".repeat(9000)
        val buffer = FlatByteArrayWriter()
        val writer = GhostYamlWriter(buffer)
        writer.beginObject().name("payload").value(longValue).endObject()
        val map = GhostYamlFlatReader(buffer.toByteArray()).readDocument() as Map<*, *>
        assertEquals(
            expected = longValue,
            actual = map["payload"]
        )
    }

    @Test
    fun flatAndStreamingWritersProduceEquivalentDocuments() {
        val value = PairBox("chaos", 42)

        val flatBuffer = FlatByteArrayWriter()
        PairBoxSerializer.serialize(GhostYamlWriter(flatBuffer), value)
        val flatBytes = flatBuffer.toByteArray()

        val streamSink = Buffer()
        PairBoxSerializer.serialize(GhostYamlWriter(streamSink), value)
        val streamBytes = streamSink.readByteArray()

        val fromFlat = PairBoxSerializer.deserialize(GhostYamlFlatReader(flatBytes))
        val fromStream = PairBoxSerializer.deserialize(GhostYamlFlatReader(streamBytes))

        assertEquals(
            expected = value,
            actual = fromFlat
        )
        assertEquals(
            expected = value,
            actual = fromStream
        )
        assertEquals(
            expected = GhostYamlFlatReader(flatBytes).readDocument() as Map<*, *>,
            actual = GhostYamlFlatReader(streamBytes).readDocument() as Map<*, *>
        )
    }

    @Test
    fun pooledReaderMatchesFreshReader() {
        val yaml = """
            left: pooled
            right: 7
        """.trimIndent().encodeToByteArray()

        val fresh = PairBoxSerializer.deserialize(GhostYamlFlatReader(yaml))
        val pooled = ghostYamlInternalUseFlatReader(yaml) { reader ->
            PairBoxSerializer.deserialize(reader)
        }
        assertEquals(
            expected = fresh,
            actual = pooled
        )
    }
}
