package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
enum class DeviceState {
    ONLINE,
    OFFLINE,
    DEGRADED,
    UNKNOWN
}

@GhostSerialization
enum class SyncStatus {
    SYNCED,
    PENDING,
    UNKNOWN
}

@GhostSerialization
data class DeviceStateWrapper(val state: DeviceState, val sync: SyncStatus)
