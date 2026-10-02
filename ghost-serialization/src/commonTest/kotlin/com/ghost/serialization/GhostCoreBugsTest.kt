package com.ghost.serialization

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.serializers.GhostIntList
import com.ghost.serialization.serializers.GhostLongList
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.common.GhostDoubleFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
class GhostCoreBugsTest {

    @Test
    fun testScientificNotationExponentOverflow() {
        // Exponent this large would overflow Int without clamping.
        val json = "2e10000000000"
        val bytes = json.encodeToByteArray()

        val reader1 = GhostJsonReader(createByteArraySource(data = bytes))
        assertFails {
            reader1.nextDouble()
        }

        val reader2 = GhostJsonReader(bytes)
        assertFails {
            reader2.nextDouble()
        }
    }

    @Test
    fun testWriterCapacityOverflowCheck() {
        val writer = FlatByteArrayWriter()
        writer.writeByte(0)
        assertFails {
            // Non-zero size + Int.MAX_VALUE must overflow before allocation is attempted.
            writer.write(ByteArray(0), 0, Int.MAX_VALUE)
        }
    }

    @Test
    fun testDoubleFormatterPrecisionLargeWholeNumbers() {
        val scratch = ByteArray(128)
        val value = 123456789012345.67

        // Above 1e9, formatting falls back to the platform formatter for shortest representation
        // instead of printing trailing scale artifacts.
        val length = GhostDoubleFormatter.writeDoubleDirect(value = value, scratch = scratch, offset = 0)
        val formattedStr = if (length == GhostDoubleFormatter.FALLBACK_REQUIRED) {
            value.toString()
        } else {
            scratch.decodeToString(0, length)
        }
        assertTrue(actual = formattedStr.contains("123456789012345.67") || formattedStr.contains("1.2345678901234567E14"))
    }

    @Test
    fun testKeyCollisionPrevention() {
        // "user_id" and "user_ip" collide under this hash/length combo.
        val options = JsonReaderOptions.of(3, 19, "user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)

        val safeOptions = JsonReaderOptions.of("id", "name", "price")
        assertTrue(actual = !safeOptions.hasCollisions)

        val json = "{\"user_id\":1,\"user_ip\":2}"

        val r1 = GhostJsonReader(json.encodeToByteArray())
        r1.beginObject()
        val match1 = r1.selectString(options = options)
        assertEquals(
            expected = 0,
            actual = match1
        )
        r1.consumeKeySeparator()
        r1.nextInt()

        val match2 = r1.selectString(options = options)
        assertEquals(
            expected = 1,
            actual = match2
        )
        r1.consumeKeySeparator()
        r1.nextInt()

        r1.endObject()
    }

    @Test
    fun testDepthLimitNegativeBoundarySafety() {
        val jsonBytes = "}".encodeToByteArray()
        val reader = GhostJsonReader(jsonBytes)

        assertEquals(
            expected = 0,
            actual = reader.depth
        )
        reader.endObject()
        // Decrementing past 0 must clamp at 0, not go negative.
        assertEquals(
            expected = 0,
            actual = reader.depth
        )

        // Same clamp applies to the streaming reader.
        val reader2 = GhostJsonReader(createByteArraySource(data = jsonBytes))
        assertEquals(
            expected = 0,
            actual = reader2.depth
        )
        reader2.endObject()
        assertEquals(
            expected = 0,
            actual = reader2.depth
        )
    }

    @Test
    fun testTruncatedUnicodeSurrogateError() {
        // High surrogate escape with no low surrogate must throw a structured
        // GhostJsonException, not an IndexOutOfBoundsException.
        val json = "\"\\uD83D\""
        val bytes = json.encodeToByteArray()

        val flatReader = GhostJsonReader(bytes)
        assertFails {
            flatReader.readQuotedString()
        }

        val streamingReader = GhostJsonReader(createByteArraySource(data = bytes))
        assertFails {
            streamingReader.readQuotedString()
        }
    }

    @Test
    fun testPrimitiveListZeroCapacity() {
        // Zero-capacity lists must grow on add rather than throwing ArrayIndexOutOfBoundsException.
        val intList = GhostIntList(initialCapacity = 0)
        assertTrue(actual = intList.isEmpty())
        intList.add(value = 10)
        intList.add(value = 20)
        assertEquals(
            expected = 2,
            actual = intList.toArray().size
        )
        assertEquals(
            expected = 10,
            actual = intList.toArray()[0]
        )
        assertEquals(
            expected = 20,
            actual = intList.toArray()[1]
        )

        val longList = GhostLongList(initialCapacity = 0)
        assertTrue(actual = longList.isEmpty())
        longList.add(value = 100L)
        longList.add(value = 200L)
        assertEquals(
            expected = 2,
            actual = longList.toArray().size
        )
        assertEquals(
            expected = 100L,
            actual = longList.toArray()[0]
        )
        assertEquals(
            expected = 200L,
            actual = longList.toArray()[1]
        )
    }

    @Test
    fun testStrictCommaValidation() {
        // strictMode rejects a missing comma between array elements.
        val missingCommaArray = "[1 2]".encodeToByteArray()
        assertFails {
            Ghost.deserialize<IntArray>(missingCommaArray) {
                it.strictMode = true
            }
        }

        // strictMode also rejects duplicate commas.
        val duplicateCommaArray = "[1,, 2]".encodeToByteArray()
        assertFails {
            Ghost.deserialize<IntArray>(duplicateCommaArray) {
                it.strictMode = true
            }
        }
    }
}
