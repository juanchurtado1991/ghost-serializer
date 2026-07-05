package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

/**
 * Minimal sealed hierarchy mirroring SmartThings ViperPage wire shape:
 * nested `devices` array before custom `pageType` discriminator.
 */
@GhostSerialization(discriminator = "pageType")
sealed class PageWithNestedDevices {
    @GhostSerialization
    data class LoggedIn(
        val devices: List<NestedDeviceStub> = emptyList(),
        val name: String = "",
    ) : PageWithNestedDevices()
}

@GhostSerialization
data class NestedDeviceStub(
    val id: String,
)
