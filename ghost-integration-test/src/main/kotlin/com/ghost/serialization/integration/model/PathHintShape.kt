package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** Sealed without @GhostFallback — unknown/missing discriminator must throw. */
@GhostSerialization
sealed class PathHintShape {
    @GhostSerialization
    data class Circle(val r: Double) : PathHintShape()

    @GhostSerialization
    data class Square(val side: Double) : PathHintShape()
}
