@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/** Chaos/stress scenarios targeting undetected parser/writer crashes and spec violations. */
class GhostChaosTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(json.encodeToByteArray())
    }

    @Test
    fun surrogatePairDecoding() {
        // High Surrogate \uD83D + Low Surrogate \uDE00 = 😀
        val json = "{\"v\": \"\\uD83D\\uDE00\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "😀",
            actual = reader.nextString()
        )
    }

    @Test
    fun malformedSurrogateThrows() {
        // High surrogate followed by non-low surrogate
        val json = "{\"v\": \"\\uD83D\\u0020\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> {
            reader.nextString()
        }
    }

    @Test
    fun numericInfinityThrows() {
        // Double.MAX_VALUE * 10 = Infinity
        val json = "{\"v\": 1e999}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> {
            reader.nextDouble()
        }
    }

    @Test
    fun skipBalancedRespectsMaxDepth() {
        // DoS protection: nesting depth is checked even for an unknown/skipped field.
        val deepJson = "{\"unknown\": " + "[".repeat(120) + "]" + "}".repeat(120)
        val reader =
            GhostJsonReader(createByteArraySource(data = deepJson.encodeToByteArray()), maxDepth = 100)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> {
            reader.skipValue()
        }
    }

    @Test
    fun segmentedUtf8Boundary() {
        // Test reading deep characters exactly at 8192 byte boundary
        val padding = "a".repeat(8191)
        val json = "{\"v\": \"${padding}😀\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = "${padding}😀",
            actual = reader.nextString()
        )
    }
}
