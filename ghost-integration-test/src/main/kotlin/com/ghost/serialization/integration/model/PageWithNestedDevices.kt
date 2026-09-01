package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** SmartThings ViperPage wire shape: nested `devices` array before custom `pageType` discriminator. */
@GhostSerialization(discriminator = "pageType")
sealed class PageWithNestedDevices {
    @GhostSerialization
    data class LoggedIn(
        val devices: List<NestedDeviceStub> = emptyList(),
        val name: String = "",
    ) : PageWithNestedDevices()
}
