package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class InferredNestedContainer(
    val id: String,
    val event: SmartEvent,
    val commands: List<DeviceCommand>
)
