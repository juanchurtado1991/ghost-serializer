package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** Inferred sealed for required-field path via throwMissingRequiredField. */
@GhostSerialization(inferred = true)
sealed class PathHintInferredPayload {
    @GhostSerialization
    data class Alpha(val code: Int) : PathHintInferredPayload()

    @GhostSerialization
    data class Beta(val label: String) : PathHintInferredPayload()
}
