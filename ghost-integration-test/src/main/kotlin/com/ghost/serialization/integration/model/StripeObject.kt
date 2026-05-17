package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization(discriminator = "object")
sealed class StripeObject {
    @GhostSerialization
    data class Charge(val amount: Long, val currency: String) : StripeObject()

    @GhostSerialization
    data class Refund(val chargeId: String, val amount: Long) : StripeObject()
}