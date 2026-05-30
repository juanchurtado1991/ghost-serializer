package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.createByteArraySource
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.selectString
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.readQuotedString
import com.ghost.serialization.writer.FlatByteArrayWriter
import com.ghost.serialization.writer.GhostDoubleFormatter
import com.ghost.serialization.serializers.GhostIntList
import com.ghost.serialization.serializers.GhostLongList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class GhostCoreBugsTest {

    @Test
    fun testScientificNotationExponentOverflow() {
        // Test with massive exponent that would overflow Int without clamping
        val json = "2e10000000000"
        val bytes = json.encodeToByteArray()

        // 1. Test GhostJsonReader
        val reader1 = GhostJsonReader(createByteArraySource(bytes))
        assertFails {
            reader1.nextDouble()
        }

        // 2. Test GhostJsonFlatReader
        val reader2 = GhostJsonFlatReader(bytes)
        assertFails {
            reader2.nextDouble()
        }
    }

    @Test
    fun testWriterCapacityOverflowCheck() {
        val writer = FlatByteArrayWriter()
        assertFails {
            // Int.MAX_VALUE will cause requiredCapacity to overflow size + extraBytes
            writer.write(ByteArray(0), 0, Int.MAX_VALUE)
        }
    }

    @Test
    fun testDoubleFormatterPrecisionLargeWholeNumbers() {
        val scratch = ByteArray(128)
        val value = 123456789012345.67

        // Ensure that double formatting above 1e9 falls back safely to JVM/Platform standard formatter
        // which guarantees shortest representation instead of printing trailing scale artifacts.
        val length = GhostDoubleFormatter.writeDoubleDirect(
            value,
            scratch,
            0,
            fallback = { v ->
                val str = v.toString()
                val b = str.encodeToByteArray()
                b.copyInto(scratch, 0)
                b.size
            }
        )

        val formattedStr = scratch.decodeToString(0, length)
        assertTrue(formattedStr.contains("123456789012345.67") || formattedStr.contains("1.2345678901234567E14"))
    }

    @Test
    fun testKeyCollisionPrevention() {
        val options = JsonReaderOptions.of(3, 19, "user_id", "user_ip")
        assertTrue(options.hasCollisions) // Must be true due to "user_id" and "user_ip" collision

        val safeOptions = JsonReaderOptions.of("id", "name", "price")
        assertTrue(!safeOptions.hasCollisions) // Must be false since there are no collisions

        val json = "{\"user_id\":1,\"user_ip\":2}"
        
        val r1 = GhostJsonReader(json.encodeToByteArray())
        r1.beginObject()
        val match1 = r1.selectString(options)
        assertEquals(0, match1) // user_id index is 0
        r1.consumeKeySeparator()
        r1.nextInt()
        
        val match2 = r1.selectString(options)
        assertEquals(1, match2) // user_ip index is 1
        r1.consumeKeySeparator()
        r1.nextInt()
        
        r1.endObject()
    }

    @Test
    fun testDepthLimitNegativeBoundarySafety() {
        val jsonBytes = "}".encodeToByteArray()
        val reader = GhostJsonFlatReader(jsonBytes)
        
        // Depth starts at 0
        assertEquals(0, reader.depth)
        reader.endObject()
        // Decrementing past 0 should stay at 0
        assertEquals(0, reader.depth)
        
        // Verify depth is clamped at 0 for streaming reader
        val reader2 = GhostJsonReader(createByteArraySource(jsonBytes))
        assertEquals(0, reader2.depth)
        reader2.endObject()
        assertEquals(0, reader2.depth)
    }

    @Test
    fun testTruncatedUnicodeSurrogateError() {
        // Truncated string ending in a high surrogate escape block, missing the low surrogate.
        // It must throw a structured GhostJsonException instead of IndexOutOfBoundsException.
        val json = "\"\\uD83D\""
        val bytes = json.encodeToByteArray()

        // 1. Test GhostJsonFlatReader
        val flatReader = GhostJsonFlatReader(bytes)
        assertFails {
            flatReader.readQuotedString()
        }

        // 2. Test GhostJsonReader
        val streamingReader = GhostJsonReader(createByteArraySource(bytes))
        assertFails {
            streamingReader.readQuotedString()
        }
    }

    @Test
    fun testPrimitiveListZeroCapacity() {
        // GhostIntList with 0 capacity should dynamically expand without ArrayIndexOutOfBoundsException
        val intList = GhostIntList(0)
        assertTrue(intList.isEmpty())
        intList.add(10)
        intList.add(20)
        assertEquals(2, intList.toArray().size)
        assertEquals(10, intList.toArray()[0])
        assertEquals(20, intList.toArray()[1])

        // GhostLongList with 0 capacity should dynamically expand without ArrayIndexOutOfBoundsException
        val longList = GhostLongList(0)
        assertTrue(longList.isEmpty())
        longList.add(100L)
        longList.add(200L)
        assertEquals(2, longList.toArray().size)
        assertEquals(100L, longList.toArray()[0])
        assertEquals(200L, longList.toArray()[1])
    }

    @Test
    fun testStrictCommaValidation() {
        // Missing comma in array should trigger assertion failures when strictMode is enabled
        val missingCommaArray = "[1 2]".encodeToByteArray()
        assertFails {
            Ghost.deserialize<IntArray>(missingCommaArray) {
                it.strictMode = true
            }
        }

        // Duplicate commas in array should trigger failure when strictMode is enabled
        val duplicateCommaArray = "[1,, 2]".encodeToByteArray()
        assertFails {
            Ghost.deserialize<IntArray>(duplicateCommaArray) {
                it.strictMode = true
            }
        }
    }
}
