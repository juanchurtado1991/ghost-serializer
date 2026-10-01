package com.ghost.serialization

import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit

/**
 * Thread-safe map for Kotlin/Native (iOS), guarded by [objc_sync_enter]/[objc_sync_exit] —
 * the same primitive as [runSynchronized] — for correct visibility under K/N's shared-object
 * memory model.
 */
internal class IosConcurrentMap<K, V> : MutableMap<K, V> {
    private val delegate = mutableMapOf<K, V>()
    private val lock = Any()

    private inline fun <T> withLock(
        block: () -> T
    ): T {
        objc_sync_enter(lock)
        return try {
            block()
        } finally {
            objc_sync_exit(lock)
        }
    }

    override val size: Int get() = withLock { delegate.size }
    override fun clear() = withLock { delegate.clear() }

    override fun containsKey(
        key: K
    ): Boolean = withLock { delegate.containsKey(key = key) }

    override fun containsValue(
        value: V
    ): Boolean = withLock { delegate.containsValue(value = value) }

    override fun get(
        key: K
    ): V? = withLock { delegate[key] }

    override fun isEmpty(): Boolean = withLock { delegate.isEmpty() }

    override fun put(
        key: K,
        value: V
    ): V? = withLock {
        delegate.put(
            key = key,
            value = value
        )
    }

    override fun putAll(
        from: Map<out K, V>
    ) = withLock { delegate.putAll(from = from) }

    override fun remove(
        key: K
    ): V? = withLock { delegate.remove(key = key) }

    // Snapshots — callers iterate a frozen copy, never the live internal set.
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = withLock { delegate.entries.toMutableSet() }

    override val keys: MutableSet<K>
        get() = withLock { delegate.keys.toMutableSet() }

    override val values: MutableCollection<V>
        get() = withLock { delegate.values.toMutableList() }
}

