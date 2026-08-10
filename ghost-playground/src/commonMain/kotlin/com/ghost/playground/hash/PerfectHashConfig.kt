package com.ghost.playground.hash

/**
 * Perfect-hash search aligned with `PerfectHashFinder` in ghost-compiler.
 * Duplicated here so Wasm can build dispatch tables without the JVM KSP module.
 */
data class PerfectHashConfig(
    val shift: Int,
    val multiplier: Int,
    val tableSize: Int,
    val extendedKeyHash: Boolean,
)
