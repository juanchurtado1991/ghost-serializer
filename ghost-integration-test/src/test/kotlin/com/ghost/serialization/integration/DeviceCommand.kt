package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization(inferred = true)
sealed class DeviceCommand {
    @GhostSerialization
    data class Reboot(val force: Boolean = false) : DeviceCommand()

    @GhostSerialization
    data class SetBrightness(val level: Int) : DeviceCommand()

    @GhostSerialization
    data class UpdateFirmware(val url: String, val version: String) : DeviceCommand()
}
