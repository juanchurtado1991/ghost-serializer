package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Metadata representation of a subclass target during inferred sealed class deserialization.
 */
internal data class InferredSubclassModel(
    val declaration: KSClassDeclaration,
    val properties: List<GhostPropertyModel>
)
