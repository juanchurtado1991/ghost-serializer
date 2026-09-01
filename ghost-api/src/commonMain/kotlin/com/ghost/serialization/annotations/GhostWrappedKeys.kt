package com.ghost.serialization.annotations

/**
 * Collapses sibling JSON keys at the current object level into a single Kotlin property.
 * This is the inverse of [GhostWrap]: wire payloads expose flat keys (`type`, `dth`, …) while the
 * model groups them under one property.
 *
 * On deserialize, each listed [keys] entry is captured from the parent object (zero-copy
 * `RawJson` slices) and assembled into a synthetic wrapper object before the property type is
 * parsed. On serialize, the wrapper property is unwrapped back into sibling keys at the same
 * JSON depth.
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
