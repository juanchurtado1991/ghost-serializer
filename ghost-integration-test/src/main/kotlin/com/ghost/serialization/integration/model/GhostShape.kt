package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
sealed class GhostShape {
    @GhostSerialization
    data class Circle(val radius: Double) : GhostShape()

    @GhostSerialization
    data class Square(val side: Double) : GhostShape()
}