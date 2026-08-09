package com.ghost.serialization.compiler.hash

internal data class PerfectHashConfig(
    val shift: Int,
    val multiplier: Int,
    val tableSize: Int,
    val extendedKeyHash: Boolean
)
