package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFallback
import com.ghost.serialization.annotations.GhostResilient
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

@GhostSerialization
data class HomeConfig(
    val wifiSsid: String,
    val autoLock: Boolean
)

@GhostSerialization
data class SmartHome(
    val id: String,
    @GhostResilient
    val active: Boolean? = null,
    @GhostResilient
    val deviceCount: Int = 0,
    val devices: List<SmartDevice>,
    @GhostResilient
    val status: HomeStatus? = null,
    @GhostResilient
    val config: HomeConfig? = null
)

@GhostSerialization
enum class HomeStatus {
    ONLINE,
    OFFLINE
}
