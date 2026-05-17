package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class CharacterResponse(
    val info: PageInfo,
    val results: List<GhostCharacter>
)
