package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

/** Wire names that are not Kotlin identifiers or that differ only in case or `.`/`_`. */
@GhostSerialization
data class OddWireNamesModel(
    @GhostName("renamed-key") val dashed: String,
    @GhostName("with space") val spaced: Int,
    @GhostName("café") val accented: Boolean,
    @GhostName("id") val lowerId: Int,
    @GhostName("ID") val upperId: Int,
    @GhostName("a.b") val dotted: Int,
    @GhostName("a_b") val underscored: Int,
)
