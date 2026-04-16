package com.ghost.serialization.core

import com.ghost.serialization.core.parser.skipCommaIfPresent
import com.ghost.serialization.core.parser.nextNonWhitespace
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.isNextNullValue
import com.ghost.serialization.core.parser.skipValue
import com.ghost.serialization.core.parser.selectName
import com.ghost.serialization.core.parser.JsonToken
import com.ghost.serialization.core.parser.peekJsonToken
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.consumeArraySeparator
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.nextFloat
import com.ghost.serialization.core.parser.consumeNull

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
