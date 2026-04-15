package com.ghost.serialization.core

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Hyper-Performance Stress Audit.
 * Designed to break the parser/writer at physical boundaries and extreme conditions.
 */
class GhostStressAuditTest {

    @Test
    fun testSegmentBoundarySplitting() {
        // Okio segments are 8192 bytes. We want to test tokens crossing this boundary.
        val segmentSize = 8192
        
        // 1. Split a String
        val stringPadding = "a".repeat(segmentSize - 5)
        val jsonString = "{\"k\":\"${stringPadding}BC\"}"
        // "k":"aaaa... (8187 chars) BC"
        // The closing quote or part of the string will cross the boundary.
        
        val reader1 = GhostJsonReader(Buffer().writeUtf8(jsonString))
        reader1.beginObject()
        assertEquals(0, reader1.selectName(GhostJsonReader.Options.of("k")))
        reader1.consumeKeySeparator()
        assertEquals(stringPadding + "BC", reader1.nextString())
        reader1.endObject()

        // 2. Split a Number
        val numPadding = " ".repeat(segmentSize - 3)
        val jsonNum = "[$numPadding 12345]"
        val reader2 = GhostJsonReader(Buffer().writeUtf8(jsonNum))
        reader2.beginArray()
        assertEquals(12345, reader2.nextInt())
        reader2.endArray()

        // 3. Split a Boolean
        val boolPadding = " ".repeat(segmentSize - 2)
        val jsonBool = "[$boolPadding true]"
        val reader3 = GhostJsonReader(Buffer().writeUtf8(jsonBool))
        reader3.beginArray()
        assertEquals(true, reader3.hasNext())
        assertEquals(true, reader3.nextBoolean())
        reader3.endArray()
    }

    @Test
    fun testDeepNestingLimit() {
        val maxDepth = 100
        val nestedJson = "{".repeat(maxDepth + 1) + "}".repeat(maxDepth + 1)
        
        val reader = GhostJsonReader(Buffer().writeUtf8(nestedJson), maxDepth = maxDepth)
        assertFailsWith<GhostJsonException> {
            repeat(maxDepth + 1) {
                reader.beginObject()
            }
        }
    }

    @Test
    fun testMalformedJsonFuzzing() {
        val malformedInputs = listOf(
            "{ \"k\": }",
            "[1, 2, ]",
            "{\"k\": \"v\"",
            "true-false",
            "null123",
            "\"escaped\\u123z\"",
            "{\"k\": \"v\"} extra",
            "0.0.0"
        )

        malformedInputs.forEach { input ->
            val reader = GhostJsonReader(Buffer().writeUtf8(input), strictMode = true)
            assertFailsWith<GhostJsonException>("Failed to catch malformed input: $input") {
                recursiveSkip(reader)
                reader.skipWhitespace()
                if (!reader.source.exhausted()) {
                    val leftover = reader.source.readUtf8()
                    if (leftover.trim().isNotEmpty()) {
                        throw GhostJsonException("Unconsumed input: $leftover", 0, 0)
                    }
                }
            }
        }
    }

    private fun recursiveSkip(reader: GhostJsonReader) {
        reader.skipWhitespace()
        if (reader.source.exhausted()) return
        when (val b = reader.peekByte()) {
            '{'.code.toByte() -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    reader.nextKey()
                    reader.consumeKeySeparator()
                    recursiveSkip(reader)
                }
                reader.endObject()
            }
            '['.code.toByte() -> {
                reader.beginArray()
                if (reader.peekByte() != ']'.code.toByte()) {
                    while (true) {
                        recursiveSkip(reader)
                        val next = reader.nextNonWhitespace()
                        if (next == ']'.code.toByte()) {
                            reader.endArray()
                            break
                        }
                        if (next != ','.code.toByte()) throw GhostJsonException("Expected ','", 0, 0)
                        reader.internalSkip(1)
                        if (reader.peekNextByte(0) == ']'.code.toByte()) {
                            throw GhostJsonException("Trailing comma", 0, 0)
                        }
                    }
                } else {
                    reader.endArray()
                }
            }
            else -> {
                reader.skipAnyValue()
            }
        }
    }

    @Test
    fun testFloatingPointValidation() {
        val writer = GhostJsonWriter(Buffer())
        
        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Double.NaN).endArray()
        }
        
        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Double.POSITIVE_INFINITY).endArray()
        }
        
        assertFailsWith<GhostJsonException> {
            writer.beginArray().value(Float.NEGATIVE_INFINITY).endArray()
        }
    }
}
