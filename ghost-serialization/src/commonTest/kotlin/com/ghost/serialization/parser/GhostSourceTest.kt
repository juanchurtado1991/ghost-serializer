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
        val source = createByteArraySource(data = bytes)

        assertEquals(
            expected = bytes.size,
            actual = source.size
        )
        assertEquals(
            expected = 'H'.code,
            actual = source[0]
        )
        assertEquals(
            expected = ' '.code,
            actual = source[5]
        )
        assertEquals(
            expected = 't'.code,
            actual = source[bytes.size - 1]
        )
    }

    @Test
    fun testByteArraySourceRangeDecoding() {
        val bytes = "{\"key\":\"value\"}".encodeToByteArray()
        val source = createByteArraySource(data = bytes)

        assertEquals(
            expected = "key",
            actual = source.decodeToString(start = 2, end = 5)
        )
        assertEquals(
            expected = "value",
            actual = source.decodeToString(start = 8, end = 13)
        )
    }

    @Test
    fun testReaderWithCustomLimit() {
        val bytes = "1234567890".encodeToByteArray()
        val source = createByteArraySource(data = bytes)
        val reader = GhostJsonReader(source, limit = 5)

        assertEquals(
            expected = 5,
            actual = reader.limit
        )
        assertEquals(
            expected = '1'.code,
            actual = reader.source[0]
        )
        assertEquals(
            expected = 5,
            actual = source.decodeToString(start = 0, end = 5).length
        )
    }
}
