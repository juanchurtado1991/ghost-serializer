package com.ghost.serialization.sample.domain

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class CharacterResponse(
    val info: PageInfo,
    val results: List<Character>
)

@GhostSerialization
data class PageInfo(
    val count: Int,
    val pages: Int,
    val next: String? = null,
    val prev: String? = null
)

@GhostSerialization
data class Character(
    val id: Int,
    val name: String,
    val status: CharacterStatus = CharacterStatus.UNKNOWN,
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
enum class CharacterStatus {
    UNKNOWN, Alive, Dead, unknown
}

@GhostSerialization
data class LocationRef(
    val name: String,
    val url: String
)
