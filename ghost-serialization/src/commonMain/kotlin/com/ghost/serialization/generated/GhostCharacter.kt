package com.ghost.serialization.generated

import com.ghost.serialization.annotations.GhostSerialization

@com.ghost.serialization.annotations.GhostSerialization
data class GhostCharacter(
    val id: Int = 0,
    val name: String = "",
    val status: String = "",
    val species: String = "",
    val type: String = "",
    val gender: String = "",
    val origin: GhostCharacterOrigin = GhostCharacterOrigin(),
    val location: GhostCharacterOrigin = GhostCharacterOrigin(),
    val image: String = "",
    val episode: List<String> = emptyList(),
    val url: String = "",
    val created: String = ""
)

