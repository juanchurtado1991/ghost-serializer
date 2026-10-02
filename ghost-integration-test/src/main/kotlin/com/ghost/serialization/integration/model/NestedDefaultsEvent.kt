package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/**
 * More than four defaults (single-shot construction) whose defaults name classes nested in the model,
 * the shape that generated uncompilable serializers before 1.3.3 (`Unresolved reference 'Lifecycle'`).
 */
@GhostSerialization
data class NestedDefaultsEvent(
    val id: String,
    val kind: Kind = Kind.UNKNOWN,
    val lifecycleType: Lifecycle.Type = Lifecycle.Type.UNKNOWN,
    val ownerId: String? = null,
    val locationId: String? = null,
    val roomId: String? = null,
    val principal: String? = null
) {
    @GhostSerialization
    enum class Kind { CREATED, UNKNOWN }

    sealed class Lifecycle {
        @GhostSerialization
        enum class Type { CREATE, DELETE, UNKNOWN }
    }
}
