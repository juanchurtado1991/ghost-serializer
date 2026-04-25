package com.ghost.serialization.core.parser

import com.ghost.serialization.InternalGhostApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalGhostApi::class)
class GhostSourceTest {

    @Test
    fun testByteArraySourceBasicRead() {
        val bytes = "Hello Ghost".encodeToByteArray()
        val source = ByteArraySource(bytes)

        assertEquals(bytes.size, source.size)
        assertEquals('H'.code.toByte(), source[0])
        assertEquals(' '.code.toByte(), source[5])
        assertEquals('t'.code.toByte(), source[bytes.size - 1])
    }

    @Test
    fun testByteArraySourceRangeDecoding() {
        val bytes = "{\"key\":\"value\"}".encodeToByteArray()
        val source = ByteArraySource(bytes)

        // Decode "key"
        assertEquals("key", source.decodeToString(2, 5))
        // Decode "value"
        assertEquals("value", source.decodeToString(8, 13))
    }

    @Test
    fun testReaderWithCustomLimit() {
        val bytes = "1234567890".encodeToByteArray()
        val source = ByteArraySource(bytes)
        // Create a reader that only sees the first 5 bytes
        val reader = GhostJsonReader(source, limit = 5)

        assertEquals(5, reader.limit)
        assertEquals('1'.code.toByte(), reader.source[0])

        // In the reader subsystems, we always check against limit
        // so this confirms the abstraction works.
        assertEquals(5, source.decodeToString(0, 5).length)
    }
}
