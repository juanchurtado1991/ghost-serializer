package com.ghost.serialization

// Kotlin/Wasm (browser) is single-threaded; one pool instance is sufficient.
private val poolInstance = GhostPool()

internal actual fun getLocalPool(): GhostPool = poolInstance
