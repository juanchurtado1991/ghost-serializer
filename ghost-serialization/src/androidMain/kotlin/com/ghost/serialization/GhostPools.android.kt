@file:JvmName("GhostPools_jvmKt")

package com.ghost.serialization

import android.os.Looper

private val pool = ThreadLocal<GhostPool>()
private val mainThreadInstance = GhostPool()
@Volatile
private var mainThreadRef: Thread? = null

internal actual fun getLocalPool(): GhostPool {
    val currentThread = Thread.currentThread()
    if (currentThread === mainThreadRef) {
        return mainThreadInstance
    }

    val mainLooper = Looper.getMainLooper()
    if (mainLooper != null && mainLooper.thread === currentThread) {
        mainThreadRef = currentThread
        return mainThreadInstance
    }

    var local = pool.get()
    if (local == null) {
        local = GhostPool()
        pool.set(local)
    }
    return local
}

