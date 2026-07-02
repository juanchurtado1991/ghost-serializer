package com.ghost.serialization.annotations

/**
 * Marks a data class as an external-discriminator JSON envelope (webhook / SSE / EventBridge shape).
 *
 * The KSP plugin generates zero-copy payload routing on the companion serializer:
 * `routePayload(envelope)`, `parsePayload(bytes)`, and optional typed variants when
 * [GhostEnvelopePayload.target] is set.
 *
 * ### Fat envelope (SmartThings SSE)
 * One nullable [com.ghost.serialization.types.RawJson] field per event type, each tagged with
 * [GhostEnvelopePayload]:
 * ```kotlin
 * @GhostJsonEnvelope(discriminator = "eventType", timeField = "eventTime")
 * @GhostSerialization
 * data class RawSseEventEnvelope(
 *     @GhostName("eventType") val eventType: String = "",
 *     @GhostName("eventTime") val timeMillis: Long = 0L,
 *     @GhostEnvelopePayload("DEVICE_EVENT")
 *     @GhostName("deviceEvent") val deviceEvent: RawJson? = null,
 * )
 * ```
 *
 * ### Generic envelope (Stripe / GitHub / CloudEvents-like)
 * Single payload field shared by all types:
 * ```kotlin
 * @GhostJsonEnvelope(discriminator = "type", dataField = "data")
 * @GhostSerialization
 * data class WebhookEnvelope(
 *     val type: String = "",
 *     val data: RawJson? = null,
 * )
 * ```
 *
 * @param discriminator JSON field holding the wire type name (default `"type"`).
 * @param timeField Optional JSON field copied into routed results metadata (e.g. `"eventTime"`).
 * @param dataField When non-empty, enables generic single-payload mode using this JSON field name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostJsonEnvelope(
    val discriminator: String = "type",
    val timeField: String = "",
    val dataField: String = ""
)
