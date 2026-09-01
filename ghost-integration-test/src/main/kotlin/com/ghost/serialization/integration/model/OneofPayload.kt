package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/**
 * Proto3 `oneof` mapping: variant field is a sibling key, no wrapper/discriminator.
 * Composes `@GhostSerialization(inferred = true)` with `@GhostWrappedKeys` instead of
 * dedicated oneof codegen.
 */
@GhostSerialization(inferred = true)
sealed class OneofPayload {
    @GhostSerialization
    data class Text(val text: String) : OneofPayload()

    @GhostSerialization
    data class Code(val code: Int) : OneofPayload()
}
