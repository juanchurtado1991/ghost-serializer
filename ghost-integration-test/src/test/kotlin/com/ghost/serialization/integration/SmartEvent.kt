package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostSignature

@GhostSerialization(inferred = true)
sealed class SmartEvent {
    @GhostSerialization
    data class TempEvent(
        val temperature: Double,
        val unit: String
    ) : SmartEvent()

    @GhostSerialization
    data class HumidityEvent(
        val humidity: Double
    ) : SmartEvent()

    @GhostSerialization
    data class MixedEvent(
        val temperature: Double,
        val humidity: Double
    ) : SmartEvent()

    @GhostSerialization
    data class MotionEvent(
        @GhostSignature
        val motion: Boolean
    ) : SmartEvent()
}
