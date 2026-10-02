@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertSame


class ProtoWktTest {

    @Test
    fun getSerializerResolvesBuiltInWktTypes() {
        assertSame(
            expected = ProtoTimestampSerializer,
            actual = Ghost.getSerializer(ProtoTimestamp::class)
        )
        assertSame(
            expected = ProtoDurationSerializer,
            actual = Ghost.getSerializer(ProtoDuration::class)
        )
        assertSame(
            expected = ProtoFieldMaskSerializer,
            actual = Ghost.getSerializer(ProtoFieldMask::class)
        )
        assertNotNull(actual = Ghost.getSerializer(ProtoEmpty::class))
        assertNotNull(actual = Ghost.getSerializer(ProtoAny::class))
        assertNotNull(actual = Ghost.getSerializer(ProtoValue::class))
    }

    @Test
    fun fieldMaskSerializerRoundTrips() {
        val original = ProtoFieldMask(paths = listOf("user.display_name", "photo"))
        val buffer = FlatByteArrayWriter()
        val writer = GhostJsonWriter(buffer)
        ProtoFieldMaskSerializer.serialize(writer, original)
        val json = buffer.toByteArray().decodeToString()
        assertEquals(
            expected = "\"user.displayName,photo\"",
            actual = json
        )
        val restored =
            ProtoFieldMaskSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(
            expected = original,
            actual = restored
        )
        assertEquals(
            expected = original,
            actual = Ghost.deserialize(ProtoFieldMaskSerializer, json.encodeToByteArray())
        )
    }

    @Test
    fun testTimestampParse() {
        val ts = parseTimestamp(timestampString = "1972-01-01T10:00:20.021Z")
        assertEquals(
            expected = 21000000,
            actual = ts.nanos
        )
    }

    @Test
    fun testDurationRoundtrip() {
        val d = parseDuration(durationString = "1.000340012s")
        assertEquals(
            expected = 1L,
            actual = d.seconds
        )
        assertEquals(
            expected = 340012,
            actual = d.nanos
        )

        val formatted = formatDuration(duration = d)
        assertEquals(
            expected = "1.000340012s",
            actual = formatted
        )

        val dNeg = parseDuration(durationString = "-120.500s")
        assertEquals(
            expected = -120L,
            actual = dNeg.seconds
        )
        assertEquals(
            expected = -500000000,
            actual = dNeg.nanos
        )
        // Proto3 JSON always emits 3/6/9 fractional digits, never an arbitrary trim.
        assertEquals(
            expected = "-120.500s",
            actual = formatDuration(duration = dNeg)
        )
    }

    @Test
    fun testDurationInvalid() {
        assertFails { parseDuration(durationString = "10") }
        assertFails { parseDuration(durationString = "10a") }
    }
}
