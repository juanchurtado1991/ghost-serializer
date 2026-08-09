package com.ghost.serialization

import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit

/**
 * Thread-safe map for Kotlin/Native (iOS).
 *
 * All mutations and reads are guarded by [objc_sync_enter]/[objc_sync_exit] — the same
 * Objective-C @synchronized primitive used by [runSynchronized]. This guarantees
 * correct visibility under K/N's new memory model where objects are shareable across threads.
 *
 * [entries], [keys] and [values] return **snapshots** (copies) so that callers iterating
 * outside the lock cannot observe concurrent structural modifications.
 */
internal class IosConcurrentMap<K, V> : MutableMap<K, V> {
    private val delegate = mutableMapOf<K, V>()
    private val lock = Any()

    private inline fun <T> withLock(block: () -> T): T {
        objc_sync_enter(lock)
        return try {
            block()
        } finally {
            objc_sync_exit(lock)
        }
    }

    override val size: Int get() = withLock { delegate.size }
    override fun isEmpty(): Boolean = withLock { delegate.isEmpty() }
    override fun containsKey(key: K): Boolean = withLock { delegate.containsKey(key) }
    override fun containsValue(value: V): Boolean = withLock { delegate.containsValue(value) }
    override fun get(key: K): V? = withLock { delegate[key] }
    override fun put(key: K, value: V): V? = withLock { delegate.put(key, value) }
    override fun remove(key: K): V? = withLock { delegate.remove(key) }
    override fun putAll(from: Map<out K, V>) = withLock { delegate.putAll(from) }
    override fun clear() = withLock { delegate.clear() }

    // Snapshots — callers iterate a frozen copy, never the live internal set.
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = withLock { delegate.entries.toMutableSet() }
    override val keys: MutableSet<K>
        get() = withLock { delegate.keys.toMutableSet() }
    override val values: MutableCollection<V>
        get() = withLock { delegate.values.toMutableList() }
}

