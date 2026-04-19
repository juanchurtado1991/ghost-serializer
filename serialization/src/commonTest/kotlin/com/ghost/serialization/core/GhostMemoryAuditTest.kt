package com.ghostserializer.core
import kotlin.test.assertTrue

import com.ghostserializer.core.parser.GhostJsonReader
import com.ghostserializer.core.parser.nextKey
import com.ghostserializer.core.parser.consumeKeySeparator

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
