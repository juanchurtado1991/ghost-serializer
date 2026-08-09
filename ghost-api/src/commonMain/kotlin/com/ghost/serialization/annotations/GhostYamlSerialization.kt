package com.ghost.serialization.annotations

/**
 * Opt-in marker for YAML serializer generation on a model that already uses
 * [GhostSerialization] or [GhostProtoSerialization].
 *
 * Without this annotation, KSP emits JSON (and proto3 JSON mapping when applicable) only.
 * With it, KSP also emits a `GhostYamlSerializer` companion when the model shape is
 * YAML-compatible.
 *
 * **Cross-format annotations** (also honored on YAML and proto3 JSON paths when codegen runs):
 * - [GhostName] — wire key override
 * - [GhostIgnore] — skip property on read/write
 *
 * **JSON-only features** (cannot be combined with this annotation — KSP error):
 * [GhostResilient], [GhostJsonEnvelope], [GhostFlatten], [GhostWrap], [GhostWrappedKeys],
 * sealed/`inferred` polymorphism, [GhostDecoder], [GhostEncoder], contextual serializers,
 * [com.ghost.serialization.types.RawJson], non-proto [kotlin.ByteArray], and nested Ghost types
 * that fail the YAML scan.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostYamlSerialization
