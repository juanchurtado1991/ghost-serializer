package com.ghost.serialization.sample.domain

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * @Serializable for kotlinx.serialization support
 * @GhostSerialization for com.ghost.serialization support
 */

@GhostSerialization
@Serializable
data class CharacterResponse(
    val info: PageInfo,
    val results: List<GhostCharacter>
)

@GhostSerialization
@Serializable
data class PageInfo(
    val count: Int,
    val pages: Int,
    val next: String? = null,
    val prev: String? = null
)

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

@GhostSerialization
@Serializable
enum class CharacterStatus {
    @SerialName("Alive") @GhostName("Alive") Alive, 
    @SerialName("Dead") @GhostName("Dead") Dead, 
    @SerialName("unknown") @GhostName("unknown") unknown
}

@GhostSerialization
@Serializable
data class LocationRef(
    val name: String,
    val url: String
)
