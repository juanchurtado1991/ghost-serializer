package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.DeviceState
import com.ghost.serialization.integration.model.DeviceStateWrapper
import com.ghost.serialization.integration.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for Ghost 1.2.3 — auto-UNKNOWN fallback for enum deserialization.
 *
 * If an enum has a constant named UNKNOWN (any case), the compiler auto-generates
 * an else branch pointing to it so unrecognized values never throw GhostJsonException.
 */
@OptIn(InternalGhostApi::class)
class GhostFallbackEnumTest {

    @Test
    fun autoUnknown_unrecognizedValueFallsBackToUnknown() {
        val json = """{"state":"REBOOTING","sync":"SYNCED"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(DeviceState.UNKNOWN, result.state)
    }

    @Test
    fun autoUnknown_knownValueDeserializesNormally() {
        val json = """{"state":"ONLINE","sync":"SYNCED"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(DeviceState.ONLINE, result.state)
    }

    @Test
    fun autoUnknown_bothEnumsHandleUnknownValues() {
        val json = """{"state":"UPLOADING","sync":"UPLOADING"}"""
        val result = Ghost.deserialize<DeviceStateWrapper>(json)
        assertEquals(DeviceState.UNKNOWN, result.state)
        assertEquals(SyncStatus.UNKNOWN, result.sync)
    }

    @Test
    fun autoUnknown_knownSyncValueDeserializesNormally() {
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
