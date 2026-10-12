@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import kotlin.test.Test
import kotlin.test.assertFailsWith


/**
 * RFC 8259 compliance: inputs the streaming reader must reject.
 */
class GhostJsonReaderSpecComplianceTest {

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

    @Test
    fun loneHighSurrogateEscapeThrows() {
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
    fun exponentOverflowingDoubleThrows() {
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
    fun skipValueRejectsMalformedValues() {
        val malformedInputs = listOf(
            "{ \"k\": }",
            "[1, 2, ]",
            "{\"k\": \"v\"",
            "\"escaped\\u123z\""
        )

        malformedInputs.forEach { input ->
            val reader = GhostJsonReader(
                source = createByteArraySource(data = input.encodeToByteArray()),
                strictMode = true
            )
            assertFailsWith<GhostJsonException>(message = "Failed to reject malformed input: $input") {
                reader.skipValue()
            }
        }
    }
}
