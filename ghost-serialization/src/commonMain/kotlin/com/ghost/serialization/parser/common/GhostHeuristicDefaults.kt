package com.ghost.serialization.parser.common

/**
 * Baseline values behind [GhostHeuristics]'s per-platform `actual`s. Platforms that need no special
 * tuning (Native, Wasm) use these directly; JVM and Android name only the values they deviate on.
 */
internal object GhostHeuristicDefaults {
    const val BYTES_PER_KIB = 1024
    const val BYTES_PER_MIB = 1024 * BYTES_PER_KIB

    const val INITIAL_COLLECTION_CAPACITY = 10
    const val MAX_STRING_POOL_LENGTH = 64
    const val MAX_COLLECTION_SIZE = 500_000
    const val MAX_DISCRIMINATOR_PEEK_DISTANCE = 1024
    const val MAX_WARM_WRITE_BUFFER_CAPACITY = BYTES_PER_MIB
    const val MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY = 512 * BYTES_PER_KIB
}
