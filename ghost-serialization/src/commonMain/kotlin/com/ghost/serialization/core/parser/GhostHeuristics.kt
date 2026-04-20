package com.ghost.serialization.core.parser

/**
 * Platform-specific heuristics for Ghost Serialization.
 * Implements an adaptive strategy to balance memory vs performance
 * based on the execution environment. (L6 Staff Implementation)
 */
expect object GhostHeuristics {
    /**
     * The initial capacity for ArrayLists and HashMaps during deserialization.
     * Prevents excessive resizing on high-perf platforms and saves memory on mobile.
     */
    val initialCollectionCapacity: Int

    /**
     * The maximum length of a string to be pooled.
     * Prevents large payloads from polluting the heap on constrained devices.
     */
    val maxStringPoolLength: Int
}
