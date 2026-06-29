package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFallback
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
enum class DeviceState {
    ONLINE,
    OFFLINE,
    @GhostFallback
    DEGRADED
}

@GhostSerialization
enum class SyncStatus {
    SYNCED,
    PENDING,
    UNKNOWN
}

@GhostSerialization
data class DeviceStateWrapper(val state: DeviceState, val sync: SyncStatus)
