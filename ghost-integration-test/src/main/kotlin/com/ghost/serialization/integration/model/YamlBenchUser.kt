package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

/** Flat YAML benchmark fixture — no nested `@GhostSerialization` types. */
@GhostSerialization
@GhostYamlSerialization
data class YamlBenchUser(
    val id: Int,
    val name: String,
    val email: String,
    val score: Double,
    val isActive: Boolean = true,
    val role: String = "VIEWER",
    val bio: String? = null,
)
