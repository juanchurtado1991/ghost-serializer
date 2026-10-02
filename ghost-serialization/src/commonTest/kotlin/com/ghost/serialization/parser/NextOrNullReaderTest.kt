@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBooleanOrNull
import com.ghost.serialization.parser.streaming.nextIntOrNull
import com.ghost.serialization.parser.streaming.nextLongOrNull
import com.ghost.serialization.parser.streaming.nextStringOrNull
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.nextStringOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


/**
 * Covers fused `nextXOrNull` readers used by KSP-generated nullable scalar fields.
 */
class NextOrNullReaderTest {

    @Test
    fun flat_nextStringOrNull_readsPresentAndNull() {
        val reader = GhostJsonReader("""["hi",null]""".encodeToByteArray())
        reader.beginArray()
        assertEquals(
            expected = "hi",
            actual = reader.nextStringOrNull()
        )
        reader.consumeArraySeparator()
        assertNull(actual = reader.nextStringOrNull())
        reader.endArray()
    }

    @Test
    fun flat_nextLongOrNull_and_nextIntOrNull() {
        val reader = GhostJsonReader("""[42,null,-7,null]""".encodeToByteArray())
        reader.beginArray()
        assertEquals(
            expected = 42L,
            actual = reader.nextLongOrNull()
        )
        reader.consumeArraySeparator()
        assertNull(actual = reader.nextLongOrNull())
        reader.consumeArraySeparator()
        assertEquals(
            expected = -7,
            actual = reader.nextIntOrNull()
        )
        reader.consumeArraySeparator()
        assertNull(actual = reader.nextIntOrNull())
        reader.endArray()
    }

    @Test
    fun flat_nextBooleanOrNull() {
        val reader = GhostJsonReader("""[true,null,false]""".encodeToByteArray())
        reader.beginArray()
        assertEquals(
            expected = true,
            actual = reader.nextBooleanOrNull()
        )
        reader.consumeArraySeparator()
        assertNull(actual = reader.nextBooleanOrNull())
        reader.consumeArraySeparator()
        assertEquals(
            expected = false,
            actual = reader.nextBooleanOrNull()
        )
        reader.endArray()
    }

    @Test
    fun flat_consumeNull_rejectsMalformedLiteral() {
        val reader = GhostJsonReader("""nu11""".encodeToByteArray())
        assertEquals(
            expected = true,
            actual = reader.isNextNullValue()
        )
        assertFailsWith<GhostJsonException> { reader.consumeNull() }
    }

    @Test
    fun string_nextStringOrNull_parity() {
        val reader = GhostJsonStringReader(rawData = """[null,"ok"]""")
        reader.beginArray()
        assertNull(actual = reader.nextStringOrNull())
        reader.consumeArraySeparator()
        assertEquals(
            expected = "ok",
            actual = reader.nextStringOrNull()
        )
        reader.endArray()
    }

    @Test
    fun streaming_nextLongOrNull_parity() {
        val reader = GhostJsonReader("""[null,99]""".encodeToByteArray())
        reader.beginArray()
        assertNull(actual = reader.nextLongOrNull())
        reader.consumeArraySeparator()
        assertEquals(
            expected = 99L,
            actual = reader.nextLongOrNull()
        )
        reader.endArray()
    }
}
