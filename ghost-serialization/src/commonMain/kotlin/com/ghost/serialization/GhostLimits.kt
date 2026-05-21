@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostHeuristics

/**
 * Global payload size limit. When unset, [GhostHeuristics.maxPayloadBytes] per platform applies.
 */
internal object GhostLimits {
    private val lock = Any()
    private var override: Int? = null

    fun effectiveMaxPayloadBytes(): Int =
        runSynchronized(lock) { override } ?: GhostHeuristics.maxPayloadBytes

    fun setMaxPayloadBytes(bytes: Int) {
        require(bytes > 0) { "maxPayloadBytes must be > 0, got $bytes" }
        runSynchronized(lock) { override = bytes }
    }

    fun resetMaxPayloadBytes() {
        runSynchronized(lock) { override = null }
    }
}
