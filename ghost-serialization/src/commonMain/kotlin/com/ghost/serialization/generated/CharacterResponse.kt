package com.ghost.serialization.generated

@com.ghost.serialization.annotations.GhostSerialization
data class CharacterResponse(
    val info: PageInfo = PageInfo(),
    val results: List<GhostCharacter> = emptyList()
)

