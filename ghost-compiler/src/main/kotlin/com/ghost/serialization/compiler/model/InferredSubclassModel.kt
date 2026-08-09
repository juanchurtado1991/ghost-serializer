package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Metadata representation of a subclass target during inferred sealed class deserialization.
 *
 * @property declaration The KSP class declaration of the sealed subclass.
 * @property properties The list of analyzed property models belonging to this subclass.
 */
internal data class InferredSubclassModel(
    val declaration: KSClassDeclaration,
    val properties: List<GhostPropertyModel>
)
