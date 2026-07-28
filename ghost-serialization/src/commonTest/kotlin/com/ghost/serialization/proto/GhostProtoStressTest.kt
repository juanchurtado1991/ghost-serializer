@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants
import com.ghost.serialization.proto.wkt.ProtoDuration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stress and repeated round-trip coverage for proto3 JSON entry points,
 * with scope comparable to [com.ghost.serialization.GhostStressAuditTest] for JSON.
 */
class GhostProtoStressTest {

    @BeforeTest
    fun setup() {
        registerProtoTestFixtures()
    }

    @Test
    fun repeatedDurationRoundTripsStayStable() {
        val original = ProtoDuration(999_999L, 123_456_789)
        var current = original
        repeat(500) {
            val json = GhostProto.encodeToString(current)
            current = GhostProto.deserialize(json)
        }
        assertEquals(original, current)
    }

    @Test
    fun repeatedDeviceRoundTripsStayStable() {
        val original = ProtoEntryPointDevice(Long.MAX_VALUE, "edge-case")
        var current = original
        repeat(500) {
            val bytes = GhostProto.encodeToBytes(current)
            current = GhostProto.deserialize(bytes)
        }
        assertEquals(original, current)
    }

    @Test
    fun largeLabelPayloadRoundTrips() {
        val label = "x".repeat(100_000)
        val original = ProtoEntryPointDevice(1L, label)
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>(GhostProto.encodeToBytes(original))
        assertEquals(original, parsed)
    }

    @Test
    fun segmentBoundaryQuotedInt64String() {
        val segmentSize = GhostJsonConstants.STREAMING_BUFFER_SIZE
        val pad = " ".repeat(segmentSize - 20)
        val json = """{$pad"deviceId":"9223372036854775807","label":"boundary"}"""
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>(json)
        assertEquals(Long.MAX_VALUE, parsed.deviceId)
        assertEquals("boundary", parsed.label)
    }

    @Test
    fun manyUnknownFieldsAreSkippedWithoutCorruption() {
        val noise = (1..50).joinToString(",") { i -> """"noise$i":{"nested":[$i,$i]}""" }
        val json = """{"deviceId":"7","label":"ok",$noise}"""
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>(json)
        assertEquals(ProtoEntryPointDevice(7L, "ok"), parsed)
    }

    @Test
    fun alternatingBareAndQuotedInt64RoundTrips() {
        val bare = """{"deviceId":1,"label":"a"}"""
        val quoted = """{"deviceId":"2","label":"b"}"""
        var device = GhostProto.deserialize<ProtoEntryPointDevice>(bare)
        assertEquals(1L, device.deviceId)
        device = GhostProto.deserialize<ProtoEntryPointDevice>(quoted)
        assertEquals(2L, device.deviceId)
        device = GhostProto.deserialize<ProtoEntryPointDevice>(GhostProto.encodeToBytes(device))
        assertEquals(2L, device.deviceId)
        assertEquals("b", device.label)
    }
}
