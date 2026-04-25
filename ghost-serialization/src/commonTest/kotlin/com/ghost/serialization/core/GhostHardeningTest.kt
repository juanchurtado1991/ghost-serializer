package com.ghost.serialization.core

import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextKey
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Spec Integrity hardening tests (TDD).
 * These tests ensure compliance with RFC 8259.
 */
class GhostHardeningTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(Buffer().writeUtf8(json))
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
