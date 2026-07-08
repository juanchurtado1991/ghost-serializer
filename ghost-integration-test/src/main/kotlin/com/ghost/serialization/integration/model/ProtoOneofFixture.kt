package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

/**
 * Proto3 `oneof` JSON mapping — the payload variant's field name appears as a *sibling* of the
 * message's other fields (no wrapper key, no discriminator value), e.g. `{"id":"e1","text":"hi"}`
 * or `{"id":"e1","code":5}`. Achieved by composing two existing features rather than needing
 * dedicated oneof codegen:
 *   - `@GhostSerialization(inferred = true)` on the sealed hierarchy: picks the subclass whose
 *     required property set matches whichever wire keys are present (no discriminator field).
 *   - `@GhostWrappedKeys` on the property: collapses those sibling wire keys into one property,
 *     assembling a synthetic wrapper object only from the keys actually present on the wire —
 *     which is exactly what the `inferred` dispatch above needs to pick the right subclass.
 */
@GhostSerialization(inferred = true)
sealed class OneofPayload {
    @GhostSerialization
    data class Text(val text: String) : OneofPayload()

    @GhostSerialization
    data class Code(val code: Int) : OneofPayload()
}

@GhostProtoSerialization
data class ProtoOneofEvent(
    val id: String,
    @GhostWrappedKeys(keys = ["text", "code"])
    val payload: OneofPayload,
)
