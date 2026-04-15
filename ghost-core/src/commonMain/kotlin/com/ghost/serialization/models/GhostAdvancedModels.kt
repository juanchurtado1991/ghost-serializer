package com.ghost.serialization.models

import com.ghost.serialization.annotations.GhostSerialization

/**
 * Advanced models for Absolute Superiority certification.
 * Centrally located in core for universal registry inclusion.
 */

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
