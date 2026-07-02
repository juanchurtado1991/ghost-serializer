package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostEnvelopeFallback
import com.ghost.serialization.annotations.GhostEnvelopePayload
import com.ghost.serialization.annotations.GhostJsonEnvelope
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class DeviceEventPayload(val deviceId: String)

@GhostSerialization
data class ModeEventPayload(val mode: String)

@GhostJsonEnvelope(discriminator = "eventType", timeField = "eventTime")
@GhostSerialization
data class SseEventEnvelope(
    @GhostName("eventType") val eventType: String = "",
    @GhostName("eventTime") val timeMillis: Long = 0L,
    @GhostEnvelopePayload("DEVICE_EVENT", target = DeviceEventPayload::class)
    @GhostName("deviceEvent") val deviceEvent: RawJson? = null,
    @GhostEnvelopePayload("MODE_EVENT", target = ModeEventPayload::class)
    @GhostName("modeEvent") val modeEvent: RawJson? = null,
    @GhostEnvelopeFallback
    val unknownEvent: RawJson? = null,
)

@GhostSerialization
data class InvoicePaidPayload(val amount: Long)

@GhostJsonEnvelope(discriminator = "type", dataField = "data")
@GhostSerialization
data class WebhookEnvelope(
    val type: String = "",
    @GhostEnvelopePayload("invoice.paid", target = InvoicePaidPayload::class)
    val data: RawJson? = null,
)
