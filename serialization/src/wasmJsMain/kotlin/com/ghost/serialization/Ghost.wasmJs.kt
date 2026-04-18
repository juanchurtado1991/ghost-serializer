package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.parser.GhostJsonReader

/**
 * Wasm Discovery: Absolute Control.
 * Automated discovery via ServiceLoader is not available in Wasm (Web).
 * Registries must be added manually using Ghost.addRegistry().
 */
actual fun discoverRegistries(): List<GhostRegistry> = emptyList()

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
