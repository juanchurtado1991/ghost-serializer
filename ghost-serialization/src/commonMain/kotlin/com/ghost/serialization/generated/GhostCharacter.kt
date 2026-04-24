package com.ghost.serialization.generated

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class GhostCharacter(
    val id: Int = 0,
    val name: String = "",
    val status: CharacterStatus = CharacterStatus.unknown,
    val species: String = "",
    val type: String = "",
    val gender: String = "",
    val origin: LocationRef = LocationRef(),
    val location: LocationRef = LocationRef(),
    val image: String = "",
    val episode: List<String> = emptyList(),
    val url: String = "",
    val created: String = ""
)
