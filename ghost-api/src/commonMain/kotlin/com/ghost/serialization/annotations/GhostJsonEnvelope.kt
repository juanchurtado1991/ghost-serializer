package com.ghost.serialization.annotations

/**
 * Marks a data class as an external-discriminator JSON envelope (webhook / SSE / EventBridge shape).
 *
 * The KSP plugin generates zero-copy payload routing on the companion serializer:
 * `routePayload(envelope)`, `parsePayload(bytes)`, and optional typed variants when
 * [GhostEnvelopePayload.target] is set. Supports either a "fat" envelope (one nullable `RawJson`
 * field per event type, each tagged with [GhostEnvelopePayload]) or a generic envelope with a
 * single shared payload field (via [dataField]).
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
