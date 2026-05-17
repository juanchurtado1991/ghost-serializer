package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ComplexObject(
    val id: Int,
    val userId: Long,
    val name: String,
    val email: String,
    val score: Double,
    val rating: Float,
    val isActive: Boolean,
    val biography: String,

    val nullableAge: Int? = null,
    val nullableName: String? = null,
    val nullableScore: Double? = null,

    val defaultRole: String = "viewer",
    val defaultPriority: Priority = Priority.LOW,
    val defaultCount: Int = 0,

    val priority: Priority,
    val tags: List<String>,
    val scores: List<Double>,
    val metadata: Map<String, String>,
    val address: Address,
    val tagObjects: List<Tag>,
    val nestedTree: NestedContainer
)