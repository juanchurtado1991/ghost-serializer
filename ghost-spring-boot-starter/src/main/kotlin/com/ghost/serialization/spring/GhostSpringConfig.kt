package com.ghost.serialization.spring

internal object GhostSpringConfig {
    val strict = ThreadLocal.withInitial { false }
    val coerce = ThreadLocal.withInitial { false }
}
