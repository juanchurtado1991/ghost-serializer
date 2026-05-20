package com.ghost.serialization

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var poolInstance = GhostPool()

internal actual fun getLocalPool(): GhostPool = poolInstance
