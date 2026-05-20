@file:JvmName("GhostPools_jvmKt")

package com.ghost.serialization

private val pool = ThreadLocal<GhostPool>()

internal actual fun getLocalPool(): GhostPool {
    var local = pool.get()
    if (local == null) {
        local = GhostPool()
        pool.set(local)
    }
    return local
}
