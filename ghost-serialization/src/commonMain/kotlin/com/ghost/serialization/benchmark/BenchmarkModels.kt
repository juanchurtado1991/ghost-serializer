package com.ghost.serialization.benchmark

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@GhostSerialization
@Serializable
data class BenchUser(
    val id: Int,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val score: Double
)

@GhostSerialization
@Serializable
enum class UserRole {
    ADMIN, USER, GUEST, EDITOR
}

@Suppress("ArrayInDataClass")
@GhostSerialization
@Serializable
data class ExtremeMetadata(
    val timestamp: Long,
    val role: UserRole,
    val tags: List<String>,
    val precision: Double,
    val history: IntArray
)

@GhostSerialization
@Serializable
data class ComplexResponse(
    val status: String,
    val users: List<BenchUser>,
    val metadata: ExtremeMetadata,
    val shards: Map<String, String>
)

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
    @SerialName("Alive")
    @GhostName("Alive")
    Alive,
    @SerialName("Dead")
    @GhostName("Dead")
    Dead,
    @SerialName("unknown")
    @GhostName("unknown")
    unknown
}

@GhostSerialization
@Serializable
data class LocationRef(
    val name: String,
    val url: String
)
