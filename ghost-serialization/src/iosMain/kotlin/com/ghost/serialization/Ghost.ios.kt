package com.ghost.serialization

import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = IosConcurrentMap()

actual fun <T> runSynchronized(
    lock: Any,
    block: () -> T
): T = try {
    objc_sync_enter(lock)
    block()
} finally {
    objc_sync_exit(lock)
}
