package com.ghost.serialization.core
import com.ghost.serialization.core.parser.Options

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
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotSame

class GhostMemoryAuditTest {

    @Test
    fun testStringPoolingReusesMemoryReference() {
        // Given
        val json = """{"status": "success", "level": "success", "key": "success"}"""
        val reader = GhostJsonReader(Buffer().writeUtf8(json))

        // When
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

        // Then
        assertEquals("status", key1)
        assertEquals("success", val1)
        assertEquals("level", key2)
        assertEquals("success", val2)
        assertEquals("key", key3)
        assertEquals("success", val3)

        // MEMORY AUDIT: Strict JVM reference equality check.
        assertSame(val1, val2, "Memory leak: Duplicate string allocation detected for 'success'")
        assertSame(val2, val3, "Memory leak: Duplicate string allocation detected for 'success'")
    }

    @Test
    fun testPoolCorrectlyFallsBackForLongStrings() {
        // Given
        val longString = "A".repeat(100) // greater than 64
        val json = """{"key1": "$longString", "key2": "$longString"}"""
        val reader = GhostJsonReader(Buffer().writeUtf8(json))

        // When
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val val1 = reader.nextString()
        reader.nextKey()
        reader.consumeKeySeparator()
        val val2 = reader.nextString()

        // Then
        assertEquals(longString, val1)
        assertEquals(longString, val2)

        // MEMORY AUDIT: Overly long strings should bypass the pool entirely.
        assertNotSame(val1, val2)
    }
}
