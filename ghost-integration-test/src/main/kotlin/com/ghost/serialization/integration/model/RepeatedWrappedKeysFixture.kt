package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

@GhostSerialization
data class RepeatedWrappedKeysFixture(
    val id: String,
    @GhostWrappedKeys(keys = ["extra1", "extra2"])
    @GhostName("extras12")
    val extras12: WireExtras12,
    @GhostWrappedKeys(keys = ["extra3", "extra4"])
    @GhostName("extras34")
    val extras34: WireExtras34,
)
