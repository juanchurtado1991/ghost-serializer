@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readSet
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.readSet
import com.ghost.serialization.serializers.ByteSerializer
import com.ghost.serialization.serializers.CharSerializer
import com.ghost.serialization.serializers.FloatSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.serializers.ShortSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.types.RawJsonSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame


/** Tri-channel coverage for built-in serializers, pending the 1.2.5 release. */
class FeatureTriChannelSerializerTest {

    @Test
    fun rawJsonSerializerRoundTripsOnAllReaders() {
        val json = """{"enabled":true}"""
        val bytes = json.encodeToByteArray()

        val fromFlat = RawJsonSerializer.deserialize(GhostJsonReader(bytes))
        val fromStreaming = RawJsonSerializer.deserialize(GhostJsonReader(bytes))
        val fromString = RawJsonSerializer.deserialize(GhostJsonStringReader(rawData = json))

        assertSame(
            expected = bytes,
            actual = fromFlat.storage
        )
        assertSame(
            expected = bytes,
            actual = fromStreaming.storage
        )
        assertNotSame(
            illegal = bytes,
            actual = fromString.storage
        )
        assertEquals(
            expected = json,
            actual = fromString.decodeToString()
        )
    }

    @Test
    fun rawJsonSerializerWritesOnAllWriters() {
        val value = RawJsonSerializer.deserialize(
            GhostJsonReader("""{"x":1}""".encodeToByteArray())
        )

        val streamingSink = Buffer()
        RawJsonSerializer.serialize(GhostJsonWriter(streamingSink), value)
        assertEquals(
            expected = """{"x":1}""",
            actual = streamingSink.readUtf8()
        )

        val flatBytes = ghostInternalEncodeWithWriter { writer: GhostJsonWriter ->
            RawJsonSerializer.serialize(writer, value)
        }
        assertContentEquals(
            expected = """{"x":1}""".encodeToByteArray(),
            actual = flatBytes
        )

        val asString = ghostInternalEncodeToString { writer: GhostJsonStringWriter ->
            RawJsonSerializer.serialize(writer, value)
        }
        assertEquals(
            expected = """{"x":1}""",
            actual = asString
        )
    }

    @Test
    fun rawJsonStringWriterReuseDoesNotLeakComma() {
        val value = RawJsonSerializer.deserialize(
            GhostJsonReader("""{"warm":true}""".encodeToByteArray())
        )
        ghostInternalEncodeToString { writer: GhostJsonStringWriter ->
            RawJsonSerializer.serialize(writer, value)
        }
        val second = ghostInternalEncodeToString { writer: GhostJsonStringWriter ->
            RawJsonSerializer.serialize(
                writer,
                RawJsonSerializer.deserialize(
                    GhostJsonReader("""{"x":1}""".encodeToByteArray())
                ),
            )
        }
        assertEquals(
            expected = """{"x":1}""",
            actual = second
        )
    }

    @Test
    fun setSerializerRoundTripsOnAllReaders() {
        val json = """["a","b","c"]"""
        val bytes = json.encodeToByteArray()
        val serializer = SetSerializer(itemSerializer = StringSerializer)

        val fromFlat = serializer.deserialize(GhostJsonReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(rawData = json))

        assertEquals(
            expected = setOf("a", "b", "c"),
            actual = fromFlat
        )
        assertEquals(
            expected = fromFlat,
            actual = fromStreaming
        )
        assertEquals(
            expected = fromFlat,
            actual = fromString
        )
    }

    @Test
    fun setSerializerTopLevelStringUsesNativeStringReader() {
        val json = """["x","y","z"]"""
        val restored = Ghost.deserialize<Set<String>>(json)
        assertEquals(
            expected = setOf("x", "y", "z"),
            actual = restored
        )
    }

    @Test
    fun listSerializerRoundTripsOnAllReaders() {
        val json = """["a","b","c"]"""
        val bytes = json.encodeToByteArray()
        val serializer = ListSerializer(itemSerializer = StringSerializer)

        val fromFlat = serializer.deserialize(GhostJsonReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(rawData = json))

        assertEquals(
            expected = listOf("a", "b", "c"),
            actual = fromFlat
        )
        assertEquals(
            expected = fromFlat,
            actual = fromStreaming
        )
        assertEquals(
            expected = fromFlat,
            actual = fromString
        )
    }

    @Test
    fun listSerializerTopLevelStringUsesNativeStringReader() {
        val json = """["one","two"]"""
        assertEquals(
            expected = listOf("one", "two"),
            actual = Ghost.deserialize<List<String>>(json)
        )
    }

    @Test
    fun mapSerializerRoundTripsOnAllReaders() {
        val json = """{"x":1,"y":2}"""
        val bytes = json.encodeToByteArray()
        val serializer = MapSerializer(valueSerializer = IntSerializer)

        val fromFlat = serializer.deserialize(GhostJsonReader(bytes))
        val fromStreaming = serializer.deserialize(GhostJsonReader(bytes))
        val fromString = serializer.deserialize(GhostJsonStringReader(rawData = json))

        assertEquals(
            expected = mapOf("x" to 1, "y" to 2),
            actual = fromFlat
        )
        assertEquals(
            expected = fromFlat,
            actual = fromStreaming
        )
        assertEquals(
            expected = fromFlat,
            actual = fromString
        )
    }

    @Test
    fun mapSerializerTopLevelStringUsesNativeStringReader() {
        val json = """{"count":42}"""
        assertEquals(
            expected = mapOf("count" to 42),
            actual = Ghost.deserialize<Map<String, Int>>(json)
        )
    }

    @Test
    fun extendedScalarsRoundTripOnAllReaders() {
        assertScalarRoundTrip(serializer = FloatSerializer, json = "1.5", expected = 1.5f)
        assertScalarRoundTrip(serializer = ByteSerializer, json = "42", expected = 42.toByte())
        assertScalarRoundTrip(serializer = ShortSerializer, json = "8080", expected = 8080.toShort())
        assertScalarRoundTrip(serializer = CharSerializer, json = "\"Z\"", expected = 'Z')
    }

    private fun <T : Any> assertScalarRoundTrip(
        serializer: GhostSerializer<T>,
        json: String,
        expected: T
    ) {
        val bytes = json.encodeToByteArray()
        assertEquals(
            expected = expected,
            actual = serializer.deserialize(GhostJsonReader(bytes))
        )
        assertEquals(
            expected = expected,
            actual = serializer.deserialize(GhostJsonReader(bytes))
        )
        assertEquals(
            expected = expected,
            actual = serializer.deserialize(GhostJsonStringReader(rawData = json))
        )
    }

    @Test
    fun readSetBuildsHashSetOnAllReaders() {
        val json = """["x","y"]"""
        val bytes = json.encodeToByteArray()

        val flatReader = GhostJsonReader(bytes)
        val fromFlat = flatReader.readSet { flatReader.nextString() }

        val streamingReader = GhostJsonReader(bytes)
        val fromStreaming = streamingReader.readSet { streamingReader.nextString() }

        val stringReader = GhostJsonStringReader(rawData = json)
        val fromString = stringReader.readSet { stringReader.nextString() }

        assertEquals(
            expected = setOf("x", "y"),
            actual = fromFlat
        )
        assertEquals(
            expected = fromFlat,
            actual = fromStreaming
        )
        assertEquals(
            expected = fromFlat,
            actual = fromString
        )
    }
}
