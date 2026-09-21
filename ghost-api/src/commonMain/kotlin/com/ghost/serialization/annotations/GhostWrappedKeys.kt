package com.ghost.serialization.annotations

/**
 * Collapses sibling JSON keys at the current object level into a single Kotlin property.
 * Inverse of [GhostWrap]: wire payloads expose flat keys (`type`, `dth`, …) while the model
 * groups them under one property.
 *
 * Deserialize captures each [keys] entry as a zero-copy `RawJson` slice and assembles a synthetic
 * wrapper object before parsing the property type; serialize unwraps it back to sibling keys at
 * the same JSON depth.
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
