package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

@GhostProtoSerialization
@Serializable
data class ProtoValueClassCollectionFixture(
    val ids: List<ProtoAccountId>,
    val accounts: Map<String, ProtoAccountId>
)
