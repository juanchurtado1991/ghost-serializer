package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap

@GhostSerialization
data class FlattenedModel(
    val id: Int,
    @GhostFlatten("attributes.value.level")
    val level: Int,
    @GhostFlatten("attributes.status")
    val status: String,
    @GhostFlatten("metadata.author")
    val author: String? = null
)

@GhostSerialization
data class WrappedModel(
    val id: Int,
    @GhostWrap("metadata.info")
    val name: String,
    @GhostWrap("metadata.info")
    val age: Int,
    @GhostWrap("system.flags")
    val active: Boolean
)

@GhostSerialization
data class DeepFlattenedModel(
    @GhostFlatten("a.b.c.d.e.f.g")
    val value: String
)

@GhostSerialization
data class MixedStructuralModel(
    val id: Int,
    @GhostFlatten("nested.flat")
    val flatValue: String,
    @GhostWrap("wrapped.nested")
    val wrappedValue: String
)
