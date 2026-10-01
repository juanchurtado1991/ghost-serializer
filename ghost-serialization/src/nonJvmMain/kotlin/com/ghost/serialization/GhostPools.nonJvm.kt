package com.ghost.serialization

import kotlin.native.concurrent.ThreadLocal

// @ThreadLocal on Kotlin/Native; a plain single-threaded global on Kotlin/Wasm (browser).
@ThreadLocal
private var poolInstance = GhostPool()

internal actual fun getLocalPool(): GhostPool = poolInstance
