@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.util.isJvm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


class GhostMemoryAuditTest {

    @Test
    fun testStringPoolingReusesMemoryReference() {
        val json = """{"status": "success", "level": "success", "key": "success"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())

        reader.beginObject()
        val key1 = reader.nextKey()
        reader.consumeKeySeparator()
        val val1 = reader.nextString()

        val key2 = reader.nextKey()
        reader.consumeKeySeparator()
        val val2 = reader.nextString()

        val key3 = reader.nextKey()
        reader.consumeKeySeparator()
        val val3 = reader.nextString()

        reader.endObject()

        assertEquals("status", key1)
        assertEquals("success", val1)
        assertEquals("level", key2)
        assertEquals("success", val2)
        assertEquals("key", key3)
        assertEquals("success", val3)

        // Reference equality only holds where pooling is guaranteed (JVM/Android); JS interns
        // strings on its own, so this check would pass there even without pooling.
        if (isJvm) {
            assertSame(
                val1,
                val2,
                "Memory leak: Duplicate string allocation detected for 'success'"
            )
            assertSame(
                val2,
                val3,
                "Memory leak: Duplicate string allocation detected for 'success'"
            )
        }
    }

    @Test
    fun testPoolCorrectlyFallsBackForLongStrings() {
        val longString = "A".repeat(1000) // exceeds max pool limit (512 on JVM, 64 on Web)
        val json = """{"key1": "$longString", "key2": "$longString"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())

        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val val1 = reader.nextString()
        reader.nextKey()
        reader.consumeKeySeparator()
        val val2 = reader.nextString()

        assertEquals(longString, val1)
        assertEquals(longString, val2)

        // Strings past the pool limit should bypass it entirely; skipped on JS since identical
        // strings are often the same object there regardless of pooling.
        if (isJvm) {
            assertNotSame(val1, val2)
        }
    }

    @Test
    fun testVeryLongStringsWithEscapes() {
        // Newline in the middle forces StringBuilder usage instead of a direct slice.
        val part1 = "B".repeat(1000)
        val part2 = "C".repeat(1000)
        val json = """{"big": "$part1\n$part2"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())

        reader.beginObject()
        assertEquals("big", reader.nextKey())
        reader.consumeKeySeparator()
        val result = reader.nextString()

        assertEquals(2001, result.length)
        assertTrue(result.contains("\n"))
        assertEquals(part1, result.substring(0, 1000))
        assertEquals(part2, result.substring(1001))
    }

    @Test
    fun testConcurrencySafety() = runTest {
        val iterations = 50
        val jobs = List(iterations) { i ->
            launch(Dispatchers.Default) {
                val json = """{"id": $i, "name": "Thread-$i", "tag": "shared"}"""
                val reader = GhostJsonReader(json.encodeToByteArray())

                reader.beginObject()

                assertEquals("id", reader.nextKey())
                reader.consumeKeySeparator()
                assertEquals(i, reader.nextInt())

                assertEquals("name", reader.nextKey())
                reader.consumeKeySeparator()
                assertEquals("Thread-$i", reader.nextString())

                assertEquals("tag", reader.nextKey())
                reader.consumeKeySeparator()
                assertEquals("shared", reader.nextString())

                reader.endObject()
            }
        }
        jobs.forEach { it.join() }
    }

    @Test
    fun testSurrogatePairsUnicode() {
        // Given: Poo Poo emoji 💩 (U+1F4A9) and Earth 🌍 (U+1F30D)
        val emoji = "💩🌍"
        val json = """{"emoji": "$emoji", "escaped": "\uD83D\uDCA9\uD83C\uDF0D"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())

        // When
        reader.beginObject()

        assertEquals("emoji", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(emoji, reader.nextString())

        assertEquals("escaped", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(emoji, reader.nextString())

        reader.endObject()
    }
}
