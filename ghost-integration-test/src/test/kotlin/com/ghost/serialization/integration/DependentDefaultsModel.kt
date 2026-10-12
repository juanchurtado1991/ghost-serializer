package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DependentDefaultsModel(
    val base: Int = 10,
    val doubled: Int = base * 2,
    val label: String = "n$base",
    val first: Int = 1,
    val second: Int = 2,
    val third: Int = 3,
)
