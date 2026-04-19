package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization

@JvmInline
@GhostSerialization
value class GhostUserToken(val value: String)

@GhostSerialization
sealed class GhostShape {
    @GhostSerialization
    data class Circle(val radius: Double) : GhostShape()

    @GhostSerialization
    data class Square(val side: Double) : GhostShape()
}

@GhostSerialization
data class GhostAdvancedProfile(
    val token: GhostUserToken,
    val shapes: List<GhostShape>
)
