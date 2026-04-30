@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization
import com.ghost.serialization.parser.*

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Spec Integrity hardening tests (TDD).
 * These tests ensure compliance with RFC 8259.
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
        reader.beginObject().ignore()
        reader.nextKey().ignore()
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
