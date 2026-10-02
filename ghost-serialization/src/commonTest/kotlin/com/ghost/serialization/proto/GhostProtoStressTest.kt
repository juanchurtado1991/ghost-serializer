@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.proto.wkt.ProtoDuration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Stress and repeated round-trip coverage for proto3 JSON entry points,
 * with scope comparable to `GhostStressAuditTest` for JSON.
 */
class GhostProtoStressTest {

    @BeforeTest
    fun setup() {
        registerProtoTestFixtures()
    }

    @Test
    fun repeatedDurationRoundTripsStayStable() {
        val original = ProtoDuration(seconds = 999_999L, nanos = 123_456_789)
        var current = original
        repeat(500) {
            val json = GhostProto.encodeToString(current)
            current = GhostProto.deserialize(json)
        }
        assertEquals(
            expected = original,
            actual = current
        )
    }

    @Test
    fun repeatedDeviceRoundTripsStayStable() {
        val original = ProtoEntryPointDevice(deviceId = Long.MAX_VALUE, label = "edge-case")
        var current = original
        repeat(500) {
            val bytes = GhostProto.encodeToBytes(current)
            current = GhostProto.deserialize(bytes)
        }
        assertEquals(
            expected = original,
            actual = current
        )
    }

    @Test
    fun largeLabelPayloadRoundTrips() {
        val label = "x".repeat(100_000)
        val original = ProtoEntryPointDevice(deviceId = 1L, label = label)
        val parsed =
            GhostProto.deserialize<ProtoEntryPointDevice>(GhostProto.encodeToBytes(original))
        assertEquals(
            expected = original,
            actual = parsed
        )
    }

    @Test
    fun segmentBoundaryQuotedInt64String() {
        val segmentSize = WR.STREAMING_BUFFER_SIZE
        val pad = " ".repeat(segmentSize - 20)
        val json = """{$pad"deviceId":"9223372036854775807","label":"boundary"}"""
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>(json)
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = parsed.deviceId
        )
        assertEquals(
            expected = "boundary",
            actual = parsed.label
        )
    }

    @Test
    fun manyUnknownFieldsAreSkippedWithoutCorruption() {
        val noise = (1..50).joinToString(",") { i -> """"noise$i":{"nested":[$i,$i]}""" }
        val json = """{"deviceId":"7","label":"ok",$noise}"""
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>(json)
        assertEquals(
            expected = ProtoEntryPointDevice(deviceId = 7L, label = "ok"),
            actual = parsed
        )
    }

    @Test
    fun alternatingBareAndQuotedInt64RoundTrips() {
        val bare = """{"deviceId":1,"label":"a"}"""
        val quoted = """{"deviceId":"2","label":"b"}"""
        var device = GhostProto.deserialize<ProtoEntryPointDevice>(bare)
        assertEquals(
            expected = 1L,
            actual = device.deviceId
        )
        device = GhostProto.deserialize<ProtoEntryPointDevice>(quoted)
        assertEquals(
            expected = 2L,
            actual = device.deviceId
        )
        device = GhostProto.deserialize<ProtoEntryPointDevice>(GhostProto.encodeToBytes(device))
        assertEquals(
            expected = 2L,
            actual = device.deviceId
        )
        assertEquals(
            expected = "b",
            actual = device.label
        )
    }
}
