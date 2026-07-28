@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import kotlin.test.Test
import kotlin.test.assertFailsWith


/**
 * RFC 8259 compliance and spec-integrity hardening tests.
 */
class GhostHardeningTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(json.encodeToByteArray())
    }

    @Test
    fun leadingZeroThrows() {
        assertFailsWith<GhostJsonException> {
            readerOf("0123").nextInt()
        }
        assertFailsWith<GhostJsonException> {
            readerOf("-05").nextInt()
        }
    }

    @Test
    fun trailingDecimalThrows() {
        assertFailsWith<GhostJsonException> {
            readerOf("1.").nextDouble()
        }
        assertFailsWith<GhostJsonException> {
            readerOf("1.e10").nextDouble()
        }
    }

    @Test
    fun literalControlCharInStringThrows() {
        // A literal newline (0x0A) is not allowed inside a JSON string.
        val json = "{\"k\": \"line1\nline2\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> {
            reader.nextString()
        }
    }

    @Test
    fun nanNumberThrows() {
        assertFailsWith<GhostJsonException> {
            readerOf("NaN").nextDouble()
        }
    }

    @Test
    fun infinityNumberThrows() {
        assertFailsWith<GhostJsonException> {
            readerOf("Infinity").nextDouble()
        }
        assertFailsWith<GhostJsonException> {
            readerOf("-Infinity").nextDouble()
        }
    }
}
