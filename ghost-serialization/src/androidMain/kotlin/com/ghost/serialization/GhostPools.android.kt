@file:JvmName("GhostPools_jvmKt")

package com.ghost.serialization

import android.os.Looper

private val pool = ThreadLocal<GhostPool>()
private val mainThreadInstance = GhostPool()
@Volatile
private var mainThreadRef: Thread? = null
private var mainThreadResolved = false

internal actual fun getLocalPool(): GhostPool {
    val currentThread = Thread.currentThread()
    val mainRef = mainThreadRef
    if (currentThread === mainRef) {
        return mainThreadInstance
    }

    if (!mainThreadResolved) {
        val mainLooper = try {
            Looper.getMainLooper()
        } catch (e: Throwable) {
            null
        }
        if (mainLooper != null) {
            val thread = mainLooper.thread
            mainThreadRef = thread
            mainThreadResolved = true
            if (currentThread === thread) {
                return mainThreadInstance
            }
        } else {
            mainThreadResolved = true
        }
    }

    var local = pool.get()
    if (local == null) {
        local = GhostPool()
        pool.set(local)
    }
    return local
}

