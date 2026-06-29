package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.DeviceState
import com.ghost.serialization.integration.model.DeviceStateWrapper
import com.ghost.serialization.integration.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for Ghost 1.2.3:
 * - @GhostFallback on a specific enum constant
 * - Auto-UNKNOWN fallback when an enum has an UNKNOWN constant
 */
@OptIn(InternalGhostApi::class)
class GhostFallbackEnumTest {

    @Test
    fun ghostFallbackAnnotation_unknownOrdinalFallsBackToAnnotatedConstant() {
        val json = """{"state":"REBOOTING","sync":"SYNCED"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(DeviceState.DEGRADED, result.state)
    }

    @Test
    fun ghostFallbackAnnotation_knownOrdinalDeserializesNormally() {
        val json = """{"state":"ONLINE","sync":"SYNCED"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(DeviceState.ONLINE, result.state)
    }

    @Test
    fun autoUnknown_unknownOrdinalFallsBackToUnknownConstant() {
        val json = """{"state":"ONLINE","sync":"UPLOADING"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(SyncStatus.UNKNOWN, result.sync)
    }

    @Test
    fun autoUnknown_knownOrdinalDeserializesNormally() {
        val json = """{"state":"ONLINE","sync":"PENDING"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(SyncStatus.PENDING, result.sync)
    }

    @Test
    fun roundTrip_knownEnumValues() {
        val original = DeviceStateWrapper(state = DeviceState.OFFLINE, sync = SyncStatus.SYNCED)
        val json = Ghost.serialize(original)
        val restored = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(original.state, restored.state)
        assertEquals(original.sync, restored.sync)
    }
}
