@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertSame


class ProtoWktTest {

    @Test
    fun getSerializerResolvesBuiltInWktTypes() {
        assertSame(ProtoTimestampSerializer, Ghost.getSerializer(ProtoTimestamp::class))
        assertSame(ProtoDurationSerializer, Ghost.getSerializer(ProtoDuration::class))
        assertSame(ProtoFieldMaskSerializer, Ghost.getSerializer(ProtoFieldMask::class))
        assertNotNull(Ghost.getSerializer(ProtoEmpty::class))
        assertNotNull(Ghost.getSerializer(ProtoAny::class))
        assertNotNull(Ghost.getSerializer(ProtoValue::class))
    }

    @Test
    fun fieldMaskSerializerRoundTrips() {
        val original = ProtoFieldMask(listOf("user.display_name", "photo"))
        val buffer = FlatByteArrayWriter()
        val writer = GhostJsonFlatWriter(buffer)
        ProtoFieldMaskSerializer.serialize(writer, original)
        val json = buffer.toByteArray().decodeToString()
        assertEquals("\"user.displayName,photo\"", json)
        val restored =
            ProtoFieldMaskSerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        assertEquals(original, restored)
        assertEquals(
            original,
            Ghost.deserialize(ProtoFieldMaskSerializer, json.encodeToByteArray()),
        )
    }

    @Test
    fun testTimestampParse() {
        val ts = parseTimestamp("1972-01-01T10:00:20.021Z")
        assertEquals(21000000, ts.nanos)
    }

    @Test
    fun testDurationRoundtrip() {
        val d = parseDuration("1.000340012s")
        assertEquals(1L, d.seconds)
        assertEquals(340012, d.nanos)

        val formatted = formatDuration(d)
        assertEquals("1.000340012s", formatted)

        val dNeg = parseDuration("-120.500s")
        assertEquals(-120L, dNeg.seconds)
        assertEquals(-500000000, dNeg.nanos)
        // Proto3 JSON always emits 3/6/9 fractional digits, never an arbitrary trim.
        assertEquals("-120.500s", formatDuration(dNeg))
    }

    @Test
    fun testDurationInvalid() {
        assertFails { parseDuration("10") }
        assertFails { parseDuration("10a") }
    }
}
