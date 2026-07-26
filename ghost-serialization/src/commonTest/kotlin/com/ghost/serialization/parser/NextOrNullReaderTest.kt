@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
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
        val reader = GhostJsonFlatReader("""["hi",null]""".encodeToByteArray())
        reader.beginArray()
        assertEquals("hi", reader.nextStringOrNull())
        reader.consumeArraySeparator()
        assertNull(reader.nextStringOrNull())
        reader.endArray()
    }

    @Test
    fun flat_nextLongOrNull_and_nextIntOrNull() {
        val reader = GhostJsonFlatReader("""[42,null,-7,null]""".encodeToByteArray())
        reader.beginArray()
        assertEquals(42L, reader.nextLongOrNull())
        reader.consumeArraySeparator()
        assertNull(reader.nextLongOrNull())
        reader.consumeArraySeparator()
        assertEquals(-7, reader.nextIntOrNull())
        reader.consumeArraySeparator()
        assertNull(reader.nextIntOrNull())
        reader.endArray()
    }

    @Test
    fun flat_nextBooleanOrNull() {
        val reader = GhostJsonFlatReader("""[true,null,false]""".encodeToByteArray())
        reader.beginArray()
        assertEquals(true, reader.nextBooleanOrNull())
        reader.consumeArraySeparator()
        assertNull(reader.nextBooleanOrNull())
        reader.consumeArraySeparator()
        assertEquals(false, reader.nextBooleanOrNull())
        reader.endArray()
    }

    @Test
    fun flat_consumeNull_rejectsMalformedLiteral() {
        val reader = GhostJsonFlatReader("""nu11""".encodeToByteArray())
        assertEquals(true, reader.isNextNullValue())
        assertFailsWith<GhostJsonException> { reader.consumeNull() }
    }

    @Test
    fun string_nextStringOrNull_parity() {
        val reader = GhostJsonStringReader("""[null,"ok"]""")
        reader.beginArray()
        assertNull(reader.nextStringOrNull())
        reader.consumeArraySeparator()
        assertEquals("ok", reader.nextStringOrNull())
        reader.endArray()
    }

    @Test
    fun streaming_nextLongOrNull_parity() {
        val reader = GhostJsonReader("""[null,99]""".encodeToByteArray())
        reader.beginArray()
        assertNull(reader.nextLongOrNull())
        reader.consumeArraySeparator()
        assertEquals(99L, reader.nextLongOrNull())
        reader.endArray()
    }
}
