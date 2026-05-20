package com.ghost.serialization

private val pool = ThreadLocal.withInitial { GhostPool() }

internal actual fun getLocalPool(): GhostPool = pool.get()
