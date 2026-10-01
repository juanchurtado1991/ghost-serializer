package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** Enum without UNKNOWN / @GhostFallback — invalid wire values must throw. */
@GhostSerialization
enum class PathHintStrictEnum {
    Alpha,
    Beta,
}
