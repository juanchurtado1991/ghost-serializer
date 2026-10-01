package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

/** Minimal required-field probe for JSONPath / hint DX tests. */
@GhostSerialization
@GhostYamlSerialization
data class PathHintRequiredModel(
    val id: Int,
    val name: String,
)
