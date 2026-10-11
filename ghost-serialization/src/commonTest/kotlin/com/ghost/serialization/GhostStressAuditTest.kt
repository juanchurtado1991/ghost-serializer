package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/** Pushes the parser/writer to physical boundaries and extreme conditions. */
@OptIn(InternalGhostApi::class)
class GhostStressAuditTest {

    @Test
    fun testSegmentBoundarySplitting() {
        // Okio segments are 8192 bytes; tests tokens that cross this boundary.
        val segmentSize = 8192

        // 1. Split a String
        val stringPadding = "a".repeat(segmentSize - 5)
        val jsonString = "{\"k\":\"${stringPadding}BC\"}"

        val reader1 = GhostJsonReader(jsonString.encodeToByteArray())
        reader1.beginObject()
        assertEquals(
            expected = 0,
            actual = reader1.selectString(options = JsonReaderOptions.of("k"))
        )
        reader1.consumeKeySeparator()
        assertEquals(
            expected = stringPadding + "BC",
            actual = reader1.nextString()
        )
        reader1.endObject()

        // 2. Split a Number
        val numPadding = " ".repeat(segmentSize - 3)
        val jsonNum = "[$numPadding 12345]"
        val reader2 = GhostJsonReader(jsonNum.encodeToByteArray())
        reader2.beginArray()
        assertEquals(
            expected = 12345,
            actual = reader2.nextInt()
        )
        reader2.endArray()

        // 3. Split a Boolean
        val boolPadding = " ".repeat(segmentSize - 2)
        val jsonBool = "[$boolPadding true]"
        val reader3 = GhostJsonReader(jsonBool.encodeToByteArray())
        reader3.beginArray()
        assertEquals(
            expected = true,
            actual = reader3.hasNext()
        )
        assertEquals(
            expected = true,
            actual = reader3.nextBoolean()
        )
        reader3.endArray()
    }

    @Test
    fun testDeepNestingLimit() {
        val maxDepth = 100
        val nestedJson = "{".repeat(maxDepth + 1) + "}".repeat(maxDepth + 1)
        val bytes = nestedJson.encodeToByteArray()

        val reader = GhostJsonReader(createByteArraySource(data = bytes), maxDepth = maxDepth)
        assertFailsWith<GhostJsonException> {
            repeat(maxDepth + 1) {
                reader.beginObject()
            }
        }
    }

    @Test
    fun skipValueRejectsMalformedValues() {
        val malformedInputs = listOf(
            "{ \"k\": }",
            "[1, 2, ]",
            "{\"k\": \"v\"",
            "\"escaped\\u123z\""
        )

        malformedInputs.forEach { input ->
            val reader = GhostJsonReader(
                source = createByteArraySource(data = input.encodeToByteArray()),
                strictMode = true
            )
            assertFailsWith<GhostJsonException>(message = "Failed to reject malformed input: $input") {
                reader.skipValue()
            }
        }
    }

    @Test
    fun testFloatingPointValidation() {
        val writer = GhostJsonWriter(Buffer())

        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Double.NaN).endArray()
        }

        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Double.POSITIVE_INFINITY).endArray()
        }

        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Float.NEGATIVE_INFINITY).endArray()
        }
    }
}
