package com.ghost.serialization.annotations

/**
 * Generates serializers for the annotated class following proto3 JSON mapping rules:
 * `lowerCamelCase` field names, default/empty values omitted, 64-bit integers as quoted strings,
 * enums as strings, and byte arrays as Base64.
 *
 * Incompatible with [GhostJsonEnvelope], [GhostFlatten], [GhostWrap], sealed/`inferred` types on
 * the message itself, [GhostDecoder], [GhostEncoder], `RawJson`, and non-proto opaque `ByteArray`
 * — combining any of these (or [GhostResilient]) with [GhostYamlSerialization] on the same class
 * is a KSP error.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostProtoSerialization(
    val name: String = "",
)
