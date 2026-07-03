package com.ghost.serialization.annotations

/**
 * Collapses sibling JSON keys at the current object level into a single Kotlin property.
 *
 * This is the inverse of [GhostWrap]: wire payloads expose flat keys (`extra1`, `type`, `dth`, …)
 * while the model groups them under one property (`extras`, `integration`, …).
 *
 * Example — API wire vs Kotlin model:
 * ```kotlin
 * // Wire:  { "deviceId": "x", "type": "DTH", "dth": { ... } }
 * // Model: Device(deviceId, integration = Integration.Dth(...))
 *
 * @GhostSerialization
 * data class Device(
 *     val deviceId: String,
 *     @GhostWrappedKeys(keys = ["type", "dth"])
 *     @GhostName("integration")
 *     val integration: Integration,
 * )
 * ```
 *
 * During **deserialization**, each listed [keys] entry is captured from the parent object
 * (zero-copy [com.ghost.serialization.types.RawJson] slices) and assembled into a synthetic
 * wrapper object `{ "type": "...", "dth": { ... } }` before the property type is parsed.
 *
 * During **serialization**, the wrapper property is **unwrapped**: inner fields are written as
 * sibling keys at the same JSON depth (matching the wire format above).
 *
 * @param keys JSON field names at the current object level that belong to the wrapper property.
 * @param omitIfEmpty When `true`, if every captured key is absent or JSON `null`, the wrapper
 *   property is set to `null` instead of an object with all-null fields. The property must be
 *   nullable.
 * @param omitIfAbsent When any listed key is absent or JSON `null`, the wrapper property is set
 *   to `null` instead of being deserialized. Use for optional integration blocks.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class GhostWrappedKeys(
    val keys: Array<String>,
    val omitIfEmpty: Boolean = false,
    val omitIfAbsent: Array<String> = [],
)
