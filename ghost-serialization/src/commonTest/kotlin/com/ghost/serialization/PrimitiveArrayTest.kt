@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import okio.Buffer


class PrimitiveArrayTest {

    @Test
    fun testIntArrayRoundTrip() {
        val original = intArrayOf(1, 2, 3, 42, 0, -1, Int.MAX_VALUE)
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        IntArraySerializer.serialize(writer, original)
        writer.flush()

        val json = buffer.readUtf8()
        assertEquals("[1,2,3,42,0,-1,2147483647]", json)

        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = IntArraySerializer.deserialize(reader)
        assertContentEquals(original, result)

        val flatResult = IntArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertContentEquals(original, flatResult)

        val stringResult = IntArraySerializer.deserialize(GhostJsonStringReader(json))
        assertContentEquals(original, stringResult)
    }

    @Test
    fun testLongArrayRoundTrip() {
        val original = longArrayOf(1L, 2L, 42L, 0L, -1L, Long.MAX_VALUE)
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        LongArraySerializer.serialize(writer, original)
        writer.flush()

        val json = buffer.readUtf8()
        assertEquals("[1,2,42,0,-1,9223372036854775807]", json)

        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = LongArraySerializer.deserialize(reader)
        assertContentEquals(original, result)

        val flatResult = LongArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertContentEquals(original, flatResult)

        val stringResult = LongArraySerializer.deserialize(GhostJsonStringReader(json))
        assertContentEquals(original, stringResult)
    }

    @Test
    fun testEmptyArrays() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        IntArraySerializer.serialize(writer, intArrayOf())
        writer.flush()
        assertEquals("[]", buffer.readUtf8())

        val reader = GhostJsonReader("[]".encodeToByteArray())
        assertContentEquals(intArrayOf(), IntArraySerializer.deserialize(reader))
        assertContentEquals(intArrayOf(), IntArraySerializer.deserialize(GhostJsonFlatReader("[]".encodeToByteArray())))
        assertContentEquals(intArrayOf(), IntArraySerializer.deserialize(GhostJsonStringReader("[]")))
    }
}
