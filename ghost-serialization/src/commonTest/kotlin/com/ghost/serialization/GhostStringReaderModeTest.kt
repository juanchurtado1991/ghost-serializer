@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextBoolean
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.readQuotedString
import com.ghost.serialization.parser.strings.selectString
import com.ghost.serialization.parser.strings.skipValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** [GhostJsonStringReader] contract: strict mode, reader reuse, and the resilient decoder. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderModeTest {

    // ══════════════════════════════════════════════════════════════════
    // Strict mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun strictModeRejectsObjectWithoutComma() {
        val reader = stringReaderOf(json = """{"a": 1 "b": 2}""")
        reader.strictMode = true
        reader.beginObject()
        assertEquals(
            expected = "a",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        assertFailsWith<GhostJsonException> { reader.nextKey() }
    }

    @Test
    fun strictModeRejectsArrayWithoutComma() {
        val reader = stringReaderOf(json = "[1 2 3]")
        reader.strictMode = true
        reader.beginArray()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        assertFailsWith<GhostJsonException> { reader.consumeArraySeparator() }
    }

    @Test
    fun strictModeRejectsTrailingCommaInObject() {
        val reader = stringReaderOf(json = """{"a":1,}""")
        reader.strictMode = true
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.nextKey()  // trailing comma → this must throw
        }
    }

    @Test
    fun strictModeThrowsOnUnknownField() {
        val options = JsonReaderOptions.of("id")
        val json = """{"unknown":"val","id":1}"""
        val reader = stringReaderOf(json = json)
        reader.strictMode = true
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.selectString(options = options)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Reset and reuse (pool simulation)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun pooledResetAndReuseWithDifferentSizes() {
        val reader = GhostJsonStringReader(rawData = """{"short":1}""")

        reader.beginObject()
        reader.skipWhitespace()
        assertEquals(
            expected = "short",
            actual = reader.readQuotedString()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
        reader.endObject()

        val longerJson = """{"very_long_field_name_indeed":1234567890123}"""
        reader.reset(longerJson)
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals(
            expected = "very_long_field_name_indeed",
            actual = reader.readQuotedString()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1234567890123L,
            actual = reader.nextLong()
        )
        reader.endObject()

        val shortJson = """{"a":true}"""
        reader.reset(shortJson)
        reader.beginObject()
        reader.skipWhitespace()
        assertEquals(
            expected = "a",
            actual = reader.readQuotedString()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = true,
            actual = reader.nextBoolean()
        )
        reader.endObject()
    }

    @Test
    fun resetClearsAllState() {
        val reader = GhostJsonStringReader(rawData = """{"x":{"nested":1}}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.beginObject()
        assertEquals(
            expected = 2,
            actual = reader.depth
        )

        reader.reset("""{"y":2}""")
        assertEquals(
            expected = 0,
            actual = reader.depth
        )
        assertEquals(
            expected = 0,
            actual = reader.position
        )
        reader.beginObject()
        assertEquals(
            expected = "y",
            actual = reader.nextKey()
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Resilient decoder
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun resilientDecodeReturnsNullOnTypeMismatch() {
        val reader = stringReaderOf(json = """{"v":{"nested": 42}}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        val depthBefore = reader.depth

        val result = reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString() // 42 is not a string → GhostJsonException
            reader.endObject()
        }

        assertNull(actual = result)
        assertEquals(
            expected = depthBefore,
            actual = reader.depth
        )
    }

    @Test
    fun resilientDecodeRestoresPositionOnFailure() {
        // Canonical use case: lambda throws (nextString on an Int) mid-nested-object;
        // decodeResilient must restore depth + position, then skipValue so parsing continues.
        val reader = stringReaderOf(json = """{"v":{"nested": 42}, "ok":1}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        val depthBefore = reader.depth

        val result = reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString()   // 42 is not a string → GhostJsonException
            reader.endObject()
        }

        assertNull(actual = result)
        assertEquals(
            expected = depthBefore,
            actual = reader.depth
        )
        reader.skipWhitespace()
        val nextKey = reader.nextKey()
        assertEquals(
            expected = "ok",
            actual = nextKey
        )
    }

    @Test
    fun resilientDecodeDepthNotLeaked() {
        val reader = stringReaderOf(json = """{"v":{"nested": 42}}""")
        reader.beginObject()
        reader.skipWhitespace()
        reader.readQuotedString()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.depth
        )

        reader.decodeResilient {
            reader.beginObject()
            reader.skipWhitespace()
            reader.readQuotedString()
            reader.consumeKeySeparator()
            reader.nextString()
            reader.endObject()
        }
        assertEquals(
            expected = 1,
            actual = reader.depth
        )
    }
}
