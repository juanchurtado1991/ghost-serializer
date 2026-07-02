@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.readSet
import com.ghost.serialization.serializers.ByteSerializer
import com.ghost.serialization.serializers.CharSerializer
import com.ghost.serialization.serializers.FloatSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.serializers.ShortSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.types.RawJsonSerializer
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Unit-level tri-channel tests for built-in serializers added in 1.2.5.
 */
class FeatureTriChannelSerializerTest {

    @Test
    fun rawJsonSerializerRoundTripsOnAllReaders() {
        val json = """{"enabled":true}"""
        val bytes = json.encodeToByteArray()

        val fromFlat = RawJsonSerializer.deserialize(GhostJsonFlatReader(bytes))
        val fromStreaming = RawJsonSerializer.deserialize(GhostJsonReader(bytes))
        val fromString = RawJsonSerializer.deserialize(GhostJsonStringReader(json))

        assertSame(bytes, fromFlat.storage)
        assertSame(bytes, fromStreaming.storage)
        assertNotSame(bytes, fromString.storage)
        assertEquals(json, fromString.decodeToString())
    }

    @Test
    fun rawJsonSerializerWritesOnAllWriters() {
        val value = RawJsonSerializer.deserialize(
            GhostJsonFlatReader("""{"x":1}""".encodeToByteArray())
        )

        val streamingSink = Buffer()
        RawJsonSerializer.serialize(GhostJsonWriter(streamingSink), value)
        assertEquals("""{"x":1}""", streamingSink.readUtf8())

        val flatBytes = ghostInternalEncodeWithWriter { writer: GhostJsonFlatWriter ->
            RawJsonSerializer.serialize(writer, value)
        }
        assertContentEquals("""{"x":1}""".encodeToByteArray(), flatBytes)

        val asString = ghostInternalEncodeToString { writer: GhostJsonStringWriter ->
            RawJsonSerializer.serialize(writer, value)
        }
        assertEquals("""{"x":1}""", asString)
    }

    @Test
    fun setSerializerRoundTripsOnAllReaders() {
        val json = """["a","b","c"]"""
        val bytes = json.encodeToByteArray()
        val serializer = SetSerializer(StringSerializer)

        val fromFlat = serializer.deserialize(GhostJsonFlatReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(json))

        assertEquals(setOf("a", "b", "c"), fromFlat)
        assertEquals(fromFlat, fromStreaming)
        assertEquals(fromFlat, fromString)
    }

    @Test
    fun setSerializerTopLevelStringUsesNativeStringReader() {
        val json = """["x","y","z"]"""
        val restored = Ghost.deserialize<Set<String>>(json)
        assertEquals(setOf("x", "y", "z"), restored)
    }

    @Test
    fun listSerializerRoundTripsOnAllReaders() {
        val json = """["a","b","c"]"""
        val bytes = json.encodeToByteArray()
        val serializer = ListSerializer(StringSerializer)

        val fromFlat = serializer.deserialize(GhostJsonFlatReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(json))

        assertEquals(listOf("a", "b", "c"), fromFlat)
        assertEquals(fromFlat, fromStreaming)
        assertEquals(fromFlat, fromString)
    }

    @Test
    fun listSerializerTopLevelStringUsesNativeStringReader() {
        val json = """["one","two"]"""
        assertEquals(listOf("one", "two"), Ghost.deserialize<List<String>>(json))
    }

    @Test
    fun mapSerializerRoundTripsOnAllReaders() {
        val json = """{"x":1,"y":2}"""
        val bytes = json.encodeToByteArray()
        val serializer = MapSerializer(IntSerializer)

        val fromFlat = serializer.deserialize(GhostJsonFlatReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(json))

        assertEquals(mapOf("x" to 1, "y" to 2), fromFlat)
        assertEquals(fromFlat, fromStreaming)
        assertEquals(fromFlat, fromString)
    }

    @Test
    fun mapSerializerTopLevelStringUsesNativeStringReader() {
        val json = """{"count":42}"""
        assertEquals(mapOf("count" to 42), Ghost.deserialize<Map<String, Int>>(json))
    }

    @Test
    fun extendedScalarsRoundTripOnAllReaders() {
        assertScalarRoundTrip(FloatSerializer, "1.5", 1.5f)
        assertScalarRoundTrip(ByteSerializer, "42", 42.toByte())
        assertScalarRoundTrip(ShortSerializer, "8080", 8080.toShort())
        assertScalarRoundTrip(CharSerializer, "\"Z\"", 'Z')
    }

    private inline fun <T : Any> assertScalarRoundTrip(
        serializer: com.ghost.serialization.contract.GhostSerializer<T>,
        json: String,
        expected: T
    ) {
        val bytes = json.encodeToByteArray()
        assertEquals(expected, serializer.deserialize(GhostJsonFlatReader(bytes)))
        assertEquals(expected, serializer.deserialize(GhostJsonReader(bytes)))
        assertEquals(expected, serializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun readSetBuildsHashSetOnAllReaders() {
        val json = """["x","y"]"""
        val bytes = json.encodeToByteArray()

        val flatReader = GhostJsonFlatReader(bytes)
        val fromFlat = flatReader.readSet { flatReader.nextString() }

        val streamingReader = GhostJsonReader(bytes)
        val fromStreaming = streamingReader.readSet { streamingReader.nextString() }

        val stringReader = GhostJsonStringReader(json)
        val fromString = stringReader.readSet { stringReader.nextString() }

        assertEquals(setOf("x", "y"), fromFlat)
        assertEquals(fromFlat, fromStreaming)
        assertEquals(fromFlat, fromString)
    }
}
