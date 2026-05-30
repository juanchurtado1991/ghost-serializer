package com.ghost.serialization.spring

object GhostSpringConfig {
    val strict = ThreadLocal.withInitial { false }
    val coerce = ThreadLocal.withInitial { false }
}
