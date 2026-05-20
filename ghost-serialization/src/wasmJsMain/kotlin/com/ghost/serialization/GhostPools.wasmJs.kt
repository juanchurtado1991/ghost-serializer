package com.ghost.serialization

private val poolInstance = GhostPool()

internal actual fun getLocalPool(): GhostPool = poolInstance
