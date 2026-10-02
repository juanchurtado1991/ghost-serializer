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

        assertEquals(
            expected = "status",
            actual = key1
        )
        assertEquals(
            expected = "success",
            actual = val1
        )
        assertEquals(
            expected = "level",
            actual = key2
        )
        assertEquals(
            expected = "success",
            actual = val2
        )
        assertEquals(
            expected = "key",
            actual = key3
        )
        assertEquals(
            expected = "success",
            actual = val3
        )

        // Reference equality only holds where pooling is guaranteed (JVM/Android); JS interns
        // strings on its own, so this check would pass there even without pooling.
        if (isJvm) {
            assertSame(
                expected = val1,
                actual = val2,
                message = "Memory leak: Duplicate string allocation detected for 'success'"
            )
            assertSame(
                expected = val2,
                actual = val3,
                message = "Memory leak: Duplicate string allocation detected for 'success'"
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

        assertEquals(
            expected = longString,
            actual = val1
        )
        assertEquals(
            expected = longString,
            actual = val2
        )

        // Strings past the pool limit should bypass it entirely; skipped on JS since identical
        // strings are often the same object there regardless of pooling.
        if (isJvm) {
            assertNotSame(
                illegal = val1,
                actual = val2
            )
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
        assertEquals(
            expected = "big",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        val result = reader.nextString()

        assertEquals(
            expected = 2001,
            actual = result.length
        )
        assertTrue(actual = result.contains("\n"))
        assertEquals(
            expected = part1,
            actual = result.substring(0, 1000)
        )
        assertEquals(
            expected = part2,
            actual = result.substring(1001)
        )
    }

    @Test
    fun testConcurrencySafety() = runTest {
        val iterations = 50
        val jobs = List(iterations) { i ->
            launch(Dispatchers.Default) {
                val json = """{"id": $i, "name": "Thread-$i", "tag": "shared"}"""
                val reader = GhostJsonReader(json.encodeToByteArray())

                reader.beginObject()

                assertEquals(
                    expected = "id",
                    actual = reader.nextKey()
                )
                reader.consumeKeySeparator()
                assertEquals(
                    expected = i,
                    actual = reader.nextInt()
                )

                assertEquals(
                    expected = "name",
                    actual = reader.nextKey()
                )
                reader.consumeKeySeparator()
                assertEquals(
                    expected = "Thread-$i",
                    actual = reader.nextString()
                )

                assertEquals(
                    expected = "tag",
                    actual = reader.nextKey()
                )
                reader.consumeKeySeparator()
                assertEquals(
                    expected = "shared",
                    actual = reader.nextString()
                )

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

        assertEquals(
            expected = "emoji",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = emoji,
            actual = reader.nextString()
        )

        assertEquals(
            expected = "escaped",
            actual = reader.nextKey()
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = emoji,
            actual = reader.nextString()
        )

        reader.endObject()
    }
}
