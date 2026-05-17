package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class GhostCharacter(
    val id: Int,
    val name: String,
    val status: CharacterStatus = CharacterStatus.unknown,
    val species: String = "",
    val type: String = "",
    val gender: String = "",
    val origin: LocationRef,
    val location: LocationRef,
    val image: String = "",
    val episode: List<String> = emptyList(),
    val url: String = "",
    val created: String = ""
)
