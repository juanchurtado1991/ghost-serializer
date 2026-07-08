package com.ghost.protobuf.wkt

import com.ghost.protobuf.GhostProtobuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProtoWktTest {

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
