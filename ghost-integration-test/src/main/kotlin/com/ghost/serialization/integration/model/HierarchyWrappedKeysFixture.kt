package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

@GhostSerialization
data class HierarchyWrappedKeysFixture(
    @GhostWrappedKeys(keys = ["id", "extra1", "extra2", "extra3", "extra4"])
    @GhostName("wrappedKeysTestClass")
    val wrappedKeysTestClass: WrappedKeysFixture,
)
