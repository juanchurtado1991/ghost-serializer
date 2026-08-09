package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostEnvelopeFallback
import com.ghost.serialization.annotations.GhostEnvelopePayload
import com.ghost.serialization.annotations.GhostJsonEnvelope
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostJsonEnvelope(discriminator = "type", dataField = "data")
@GhostSerialization
data class WebhookEnvelope(
    val type: String = "",
    @GhostEnvelopePayload("invoice.paid", target = InvoicePaidPayload::class)
    val data: RawJson? = null,
)
