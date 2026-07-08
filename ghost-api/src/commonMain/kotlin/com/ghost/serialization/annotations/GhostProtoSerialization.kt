package com.ghost.serialization.annotations

/**
 * Triggers the automatic generation of high-performance serializers for the annotated class,
 * adhering strictly to the proto3 JSON mapping rules.
 *
 * Differences from standard `@GhostSerialization`:
 * - Fields are serialized using `lowerCamelCase` names.
 * - Fields with default/empty values are omitted by default.
 * - 64-bit integers (`int64`, `uint64`) are serialized as quoted string values.
 * - Enums are serialized as strings by default.
 * - Byte arrays are serialized as Base64 strings.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostProtoSerialization(
    val name: String = "",
)
