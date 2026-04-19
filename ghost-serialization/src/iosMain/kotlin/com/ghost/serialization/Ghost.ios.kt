package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.parser.GhostJsonReader

/**
 * iOS Implementation of Ghost Registry Discovery.
 * Stub for build completeness.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    return emptyList()
}

actual fun <T> __ghost_internal_use_reader__(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    val reader = GhostJsonReader(bytes)
    try {
        return block(reader)
    } finally {
        reader.clear()
    }
}

actual fun <T> __ghost_internal_use_source__(
    source: okio.BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val bytes = source.readByteArray()
    val reader = GhostJsonReader(bytes)
    try {
        return block(reader)
    } finally {
        reader.clear()
    }
}
