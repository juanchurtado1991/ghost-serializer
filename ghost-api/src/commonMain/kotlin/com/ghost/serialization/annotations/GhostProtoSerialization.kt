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
 *
 * **Cross-format annotations** (also honored when YAML codegen runs on the same class):
 * - [GhostName] — wire key override
 * - [GhostIgnore] — skip property on read/write
 * - [GhostYamlSerialization] — opt-in YAML on the same message
 *
 * **Proto3 JSON patterns:**
 * - [GhostWrappedKeys] + nested `@GhostSerialization(inferred = true)` types for `oneof`
 *
 * **Not for proto3 JSON** (or KSP error when combined with [GhostYamlSerialization]):
 * [GhostJsonEnvelope], [GhostFlatten], [GhostWrap], sealed/`inferred` on the message itself,
 * [GhostDecoder]/[GhostEncoder], `RawJson`, and non-proto opaque `ByteArray`.
 *
 * See the wiki *Usage — Protobuf* and *Advanced Features — Format compatibility* for the full matrix.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostProtoSerialization(
    val name: String = "",
)
