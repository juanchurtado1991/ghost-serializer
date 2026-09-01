package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import kotlin.test.Test
import kotlin.test.assertEquals


@OptIn(InternalGhostApi::class)
class GhostSourceTest {

    @Test
    fun testByteArraySourceBasicRead() {
        val bytes = "Hello Ghost".encodeToByteArray()
        val source = createByteArraySource(bytes)

        assertEquals(bytes.size, source.size)
        assertEquals('H'.code, source[0])
        assertEquals(' '.code, source[5])
        assertEquals('t'.code, source[bytes.size - 1])
    }

    @Test
    fun testByteArraySourceRangeDecoding() {
        val bytes = "{\"key\":\"value\"}".encodeToByteArray()
        val source = createByteArraySource(bytes)

        assertEquals("key", source.decodeToString(2, 5))
        assertEquals("value", source.decodeToString(8, 13))
    }

    @Test
    fun testReaderWithCustomLimit() {
        val bytes = "1234567890".encodeToByteArray()
        val source = createByteArraySource(bytes)
        val reader = GhostJsonReader(source, limit = 5)

        assertEquals(5, reader.limit)
        assertEquals('1'.code, reader.source[0])
        assertEquals(5, source.decodeToString(0, 5).length)
    }
}
