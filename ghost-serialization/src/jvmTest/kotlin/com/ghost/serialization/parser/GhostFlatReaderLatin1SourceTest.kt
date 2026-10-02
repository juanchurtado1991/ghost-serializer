@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.JvmByteArraySource
import com.ghost.serialization.parser.common.createByteArraySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * FlatReader must use [createByteArraySource] so JVM/Android get the Latin1/ISO-8859-1
 * 7-bit decode path already wired on [GhostJsonFlatReader].
 */
class GhostFlatReaderLatin1SourceTest {

    @Test
    fun flatReaderUsesJvmByteArraySource() {
        val reader = GhostJsonFlatReader(rawData = """{"a":"hello"}""".encodeToByteArray())
        assertIs<JvmByteArraySource>(reader.source)
    }

    @Test
    fun resetSliceRebindsSameJvmSourceWrapper() {
        val reader = GhostJsonFlatReader(rawData = """{"a":1}""".encodeToByteArray())
        val first = reader.source
        assertIs<JvmByteArraySource>(first)

        val next = """{"b":"world"}""".encodeToByteArray()
        reader.resetSlice(buffer = next, offset = 0, length = next.size)
        assertTrue(
            actual = reader.source === first,
            message = "resetSlice should reuse the source wrapper"
        )
        assertEquals(
            expected = next,
            actual = first.data
        )
    }

    @Test
    fun sevenBitStringsDecodeCorrectlyViaLatin1Path() {
        val json = """{"msg":"plain ascii value","n":42}""".encodeToByteArray()
        val reader = GhostJsonFlatReader(rawData = json)
        reader.beginObject()
        assertEquals(
            expected = "msg",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "plain ascii value",
            actual = reader.nextString()
        )
        assertEquals(
            expected = "n",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }
}
