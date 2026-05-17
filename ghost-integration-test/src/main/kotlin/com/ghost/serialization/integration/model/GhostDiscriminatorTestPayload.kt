package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class GhostDiscriminatorTestPayload(
    val defaultEvent: ApiEventDefault,
    val kindEvent: GhostKindEvent,
    val stripeObject: StripeObject
)