package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

/** Large opaque metadata payload for capture benchmarks. */
@GhostSerialization
data class OpaqueMetadataEnvelope(
    val id: String,
    @GhostName("metadata") val metadata: RawJson
)

/** ByteArray control model for capture benchmarks. */
@GhostSerialization
data class OpaqueMetadataByteEnvelope(
    val id: String,
    @GhostName("metadata") val metadata: ByteArray
)
