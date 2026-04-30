@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import okio.BufferedSource
import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var cachedReader: GhostJsonReader? = null

actual fun discoverRegistries(): List<GhostRegistry> = emptyList()

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = mutableMapOf()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T {
    objc_sync_enter(lock)
    try {
        return block()
    } finally {
        objc_sync_exit(lock)
    }
}

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray, block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(bytes)
            .also { cachedReader = it }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(source
        )
            .also { cachedReader = it }
    reader.reset(source)
    return block(reader)
}
