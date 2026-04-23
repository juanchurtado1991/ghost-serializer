package com.ghost.serialization.generated

import com.ghost.serialization.annotations.GhostSerialization

@com.ghost.serialization.annotations.GhostSerialization
data class CharacterResponse(
    val info: CharacterResponseInfo = CharacterResponseInfo(),
    val results: List<GhostCharacter> = emptyList()
)

