package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
enum class DeviceState {
    ONLINE,
    OFFLINE,
    DEGRADED,
    UNKNOWN
}
