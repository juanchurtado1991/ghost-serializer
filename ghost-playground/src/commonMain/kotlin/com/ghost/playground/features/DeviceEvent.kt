package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostFallback
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
sealed class DeviceEvent {
    @GhostSerialization
    data class Status(val ok: Boolean) : DeviceEvent()

    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : DeviceEvent()
}
