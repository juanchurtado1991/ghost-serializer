package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * One wire field emitted when unwrapping a `@GhostWrappedKeys` property during serialization.
 *
 * @property jsonName JSON key written at the parent object level.
 * @property kotlinPath Property accessor segments from the wrapper value (e.g. `["extras", "extra1"]`).
 * @property isNullable Whether the leaf property accepts JSON null / omission.
 * @property typeName Kotlin type of the leaf property for serialization dispatch.
 * @property sealedSubclassName Non-null when this field was resolved from a sealed subclass of
 *   the wrapped type rather than the wrapped type's own properties (proto3 `oneof` mapping: each
 *   wire key corresponds to one subclass' field, e.g. `Text.text`/`Code.code` on a
 *   `sealed class Payload`). Emission must `is`-check/smart-cast to this subclass before
 *   accessing [kotlinPath] on it.
 */
internal data class WrappedUnwrapFieldModel(
    val jsonName: String,
    val kotlinPath: List<String>,
    val isNullable: Boolean,
    val typeName: TypeName,
    val type: KSType,
    val sealedSubclassName: ClassName? = null,
)
