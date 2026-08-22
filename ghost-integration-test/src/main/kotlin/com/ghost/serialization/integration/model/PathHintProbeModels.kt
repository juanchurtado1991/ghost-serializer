package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostResilient
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

/** Minimal required-field probe for JSONPath / hint DX tests. */
@GhostSerialization
@GhostYamlSerialization
data class PathHintRequiredModel(
    val id: Int,
    val name: String,
)

/** Enum without UNKNOWN / @GhostFallback — invalid wire values must throw. */
@GhostSerialization
enum class PathHintStrictEnum {
    Alpha,
    Beta,
}

@GhostSerialization
data class PathHintEnumHolder(
    val status: PathHintStrictEnum,
)

/** Sealed without @GhostFallback — unknown/missing discriminator must throw. */
@GhostSerialization
sealed class PathHintShape {
    @GhostSerialization
    data class Circle(val r: Double) : PathHintShape()

    @GhostSerialization
    data class Square(val side: Double) : PathHintShape()
}

@GhostSerialization
data class PathHintShapeHolder(
    val shape: PathHintShape,
)

/** Nested list path: `$.user.addresses[1].zip`. */
@GhostSerialization
data class PathHintAddress(
    val zip: Int,
)

@GhostSerialization
data class PathHintUser(
    val addresses: List<PathHintAddress>,
)

@GhostSerialization
data class PathHintNestedRoot(
    val user: PathHintUser,
)

/** Soft field recovers; hard field must still report a clean path. */
@GhostSerialization
data class PathHintResilientHolder(
    @GhostResilient
    val soft: Int? = null,
    val hard: Int,
)

/** Inferred sealed for required-field path via throwMissingRequiredField. */
@GhostSerialization(inferred = true)
sealed class PathHintInferredPayload {
    @GhostSerialization
    data class Alpha(val code: Int) : PathHintInferredPayload()

    @GhostSerialization
    data class Beta(val label: String) : PathHintInferredPayload()
}

@GhostSerialization
data class PathHintInferredHolder(
    val payload: PathHintInferredPayload,
)
