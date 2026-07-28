package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.DeviceState
import com.ghost.serialization.integration.model.DeviceStateWrapper
import com.ghost.serialization.integration.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Auto-UNKNOWN fallback for enum deserialization.
 *
 * When an enum defines an UNKNOWN constant (any case), the compiler generates an else branch
 * so unrecognized wire values map to it instead of throwing GhostJsonException.
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
