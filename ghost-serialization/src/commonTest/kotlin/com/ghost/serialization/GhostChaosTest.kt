@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.writer.common.*
import com.ghost.serialization.writer.strings.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.writer.bytes.*
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.common.createByteArraySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Chaos Engineering Suite (TDD).
 * Target: Undetected crashes and spec violations.
 */
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
        assertEquals("😀", reader.nextString())
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
        // Test DoS protection on unknown fields
        val deepJson = "{\"unknown\": " + "[".repeat(120) + "]" + "}".repeat(120)
        // Explicitly set maxDepth to 100 to trigger failure
        val reader =
            GhostJsonReader(createByteArraySource(deepJson.encodeToByteArray()), maxDepth = 100)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        // Should fail because total depth (1 from current object + 120 from skip) > 100
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
        assertEquals("${padding}😀", reader.nextString())
    }
}
