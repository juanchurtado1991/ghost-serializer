package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.parser.GhostJsonReader

/**
 * JS Discovery.
 */
actual fun discoverRegistries(): List<GhostRegistry> = emptyList()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T {
    return block()
}

actual fun <T> ghostInternalUseReader(
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

actual fun <T> ghostInternalUseSource(
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
