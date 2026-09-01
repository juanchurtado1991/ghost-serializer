package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

/** Large opaque metadata payload for capture benchmarks. */
@GhostSerialization
data class OpaqueMetadataByteEnvelope(
    val id: String,
    @GhostName("metadata") val metadata: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as OpaqueMetadataByteEnvelope
        return id == other.id && metadata.contentEquals(other.metadata)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + metadata.contentHashCode()
        return result
    }
}
