package com.ghost.serialization.annotations

/**
 * Opt-in marker for YAML serializer generation on a model that already uses
 * [GhostSerialization] or [GhostProtoSerialization]. Emits a `GhostYamlSerializer` companion
 * when the model shape is YAML-compatible.
 *
 * KSP error if combined with [GhostResilient], [GhostJsonEnvelope], [GhostFlatten], [GhostWrap],
 * [GhostWrappedKeys], sealed/`inferred` polymorphism, [GhostDecoder], [GhostEncoder], contextual
 * serializers, `RawJson`, non-proto `ByteArray`, or nested Ghost types that fail the YAML scan.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostYamlSerialization
