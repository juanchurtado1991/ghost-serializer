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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PrimitiveArrayTest {

    @Test
    fun testIntArrayRoundTrip() {
        val original = intArrayOf(1, 2, 3, 42, 0, -1, Int.MAX_VALUE)
        val buffer = Buffer()
        IntArraySerializer.serialize(buffer, original)
        
        val json = buffer.readUtf8()
        assertEquals("[1,2,3,42,0,-1,2147483647]", json)
        
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        val result = IntArraySerializer.deserialize(reader)
        assertContentEquals(original, result)
    }

    @Test
    fun testLongArrayRoundTrip() {
        val original = longArrayOf(1L, 2L, 42L, 0L, -1L, Long.MAX_VALUE)
        val buffer = Buffer()
        LongArraySerializer.serialize(buffer, original)
        
        val json = buffer.readUtf8()
        assertEquals("[1,2,42,0,-1,9223372036854775807]", json)
        
        val reader = GhostJsonReader(Buffer().writeUtf8(json))
        val result = LongArraySerializer.deserialize(reader)
        assertContentEquals(original, result)
    }

    @Test
    fun testEmptyArrays() {
        val buffer = Buffer()
        IntArraySerializer.serialize(buffer, intArrayOf())
        assertEquals("[]", buffer.readUtf8())
        
        val reader = GhostJsonReader(Buffer().writeUtf8("[]"))
        assertContentEquals(intArrayOf(), IntArraySerializer.deserialize(reader))
    }
}
