package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * One wire field emitted when unwrapping a `@GhostWrappedKeys` property during serialization.
 *
 * @property sealedSubclassName Non-null when this field comes from a sealed subclass of the
 *   wrapped type, not its own properties (proto3 `oneof`: each wire key maps to one subclass'
 *   field, e.g. `Text.text`/`Code.code`). Emission must smart-cast to this subclass first.
 */
internal data class WrappedUnwrapFieldModel(
    val jsonName: String,
    val kotlinPath: List<String>,
    val isNullable: Boolean,
    val typeName: TypeName,
    val type: KSType,
    val sealedSubclassName: ClassName? = null,
)
