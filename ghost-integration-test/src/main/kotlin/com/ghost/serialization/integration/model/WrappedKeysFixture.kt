package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

@GhostSerialization
data class WireExtras(
    @GhostName("extra1") val extra1: String?,
    @GhostName("extra2") val extra2: String?,
    @GhostName("extra3") val extra3: String?,
    @GhostName("extra4") val extra4: String?,
)

@GhostSerialization
data class WrappedKeysFixture(
    val id: String,
    @GhostWrappedKeys(keys = ["extra1", "extra2", "extra3", "extra4"])
    @GhostName("extras")
    val extras: WireExtras,
)

@GhostSerialization
data class OmitIfEmptyWrappedKeysFixture(
    val id: String,
    @GhostWrappedKeys(keys = ["extra1", "extra2", "extra3", "extra4"], omitIfEmpty = true)
    @GhostName("extras")
    val extras: WireExtras?,
)

@GhostSerialization
data class OmitIfAbsentWrappedKeysFixture(
    val id: String,
    @GhostWrappedKeys(
        keys = ["extra1", "extra2", "extra3", "extra4"],
        omitIfAbsent = ["extra2", "extra4"],
    )
    @GhostName("extras")
    val extras: WireExtras?,
)

@GhostSerialization
data class HierarchyWrappedKeysFixture(
    @GhostWrappedKeys(keys = ["id", "extra1", "extra2", "extra3", "extra4"])
    @GhostName("wrappedKeysTestClass")
    val wrappedKeysTestClass: WrappedKeysFixture,
)

@GhostSerialization
data class WireExtras12(
    @GhostName("extra1") val extra1: String,
    @GhostName("extra2") val extra2: String,
)

@GhostSerialization
data class WireExtras34(
    @GhostName("extra3") val extra3: String,
    @GhostName("extra4") val extra4: String,
)

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
