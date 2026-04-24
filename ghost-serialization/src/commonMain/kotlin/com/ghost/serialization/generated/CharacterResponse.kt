package com.ghost.serialization.generated

@com.ghost.serialization.annotations.GhostSerialization
data class CharacterResponse(
    val info: CharacterResponseInfo = CharacterResponseInfo(),
    val results: List<GhostCharacter> = emptyList()
)

