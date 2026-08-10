package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

@GhostSerialization
data class OmitIfEmptyWrappedKeysFixture(
    val id: String,
    @GhostWrappedKeys(keys = ["extra1", "extra2", "extra3", "extra4"], omitIfEmpty = true)
    @GhostName("extras")
    val extras: WireExtras?,
)
