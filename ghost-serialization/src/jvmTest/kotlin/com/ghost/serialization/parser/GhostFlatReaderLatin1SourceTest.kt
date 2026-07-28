@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.JvmByteArraySource
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader


/**
 * FlatReader must use [createByteArraySource] so JVM/Android get the Latin1/ISO-8859-1
 * 7-bit decode path already wired on [GhostJsonReader].
 */
class GhostFlatReaderLatin1SourceTest {

    @Test
    fun flatReaderUsesJvmByteArraySource() {
        val reader = GhostJsonFlatReader("""{"a":"hello"}""".encodeToByteArray())
        assertIs<JvmByteArraySource>(reader.source)
    }

    @Test
    fun resetSliceRebindsSameJvmSourceWrapper() {
        val reader = GhostJsonFlatReader("""{"a":1}""".encodeToByteArray())
        val first = reader.source
        assertIs<JvmByteArraySource>(first)

        val next = """{"b":"world"}""".encodeToByteArray()
        reader.resetSlice(next, 0, next.size)
        assertTrue(reader.source === first, "resetSlice should reuse the source wrapper")
        assertEquals(next, reader.source.data)
    }

    @Test
    fun sevenBitStringsDecodeCorrectlyViaLatin1Path() {
        val json = """{"msg":"plain ascii value","n":42}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(json)
        reader.beginObject()
        assertEquals("msg", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("plain ascii value", reader.nextString())
        assertEquals("n", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        reader.endObject()
    }
}
