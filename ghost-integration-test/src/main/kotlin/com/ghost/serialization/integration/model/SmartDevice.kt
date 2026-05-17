package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFallback
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
sealed class SmartDevice {
    @GhostSerialization
    data class Light(val brightness: Int) : SmartDevice()

    @GhostSerialization
    data class Thermostat(val temperature: Double) : SmartDevice()

    @GhostFallback
    @GhostSerialization
    data class UnknownDevice(val rawData: String = "unknown") : SmartDevice()
}