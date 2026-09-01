package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.TypeName

/**
 * Metadata for a serializable property: type resolutions, constraints, annotations, and
 * hierarchical structures resolved during compiler analysis to drive codegen.
 *
 * @property jsonName Target JSON field name, resolved from @GhostName if present.
 * @property defaultExpression Whitelisted Kotlin source of the ctor default (`"viewer"`, `1`,
 *   `emptyList()`, …), or `null` when unavailable / unsafe — callers must fall back to `.copy()`.
 * @property isGhost True if the property's type is itself annotated with @GhostSerialization.
 * @property isResilient True if this property or class is marked with @GhostResilient.
 * @property flattenPath Nested JSON path if this property is flattened via @GhostFlatten.
 * @property wrapPath Nested JSON path if this property is wrapped via @GhostWrap.
 * @property wrappedSourceKeys Wire keys collapsed into this property via @GhostWrappedKeys.
 * @property wrappedOmitIfEmpty When true, absent/null-only captures yield a null wrapper property.
 * @property wrappedOmitIfAbsent Keys that force a null wrapper when absent or JSON null.
 * @property isProto True if the enclosing class is `@GhostProtoSerialization`: `Long` fields are
 *   read/written as quoted decimal strings instead of bare JSON numbers.
 */
internal data class GhostPropertyModel(
    val kotlinName: String,
    val jsonName: String,
    val type: KSType,
    val typeName: TypeName,
    val isNullable: Boolean,
    val isGhost: Boolean,
    val isList: Boolean,
    val isSet: Boolean = false,
    val isEnum: Boolean,
    val listInnerType: KSType? = null,
    val listInnerIsGhost: Boolean = false,
    val listInnerIsEnum: Boolean = false,
    val hasDefaultValue: Boolean = false,
    val defaultExpression: String? = null,
    val isInConstructor: Boolean = true,
    val isMap: Boolean = false,
    val mapValueType: KSType? = null,
    val mapValueIsGhost: Boolean = false,
    val isPrimitiveArray: Boolean = false,
    val primitiveArrayType: String? = null,
    val isValueClass: Boolean = false,
    val valueClassProperty: GhostPropertyModel? = null,
    val isSealedClass: Boolean = false,
    val sealedSubclasses: List<KSClassDeclaration> = emptyList(),
    val enumValues: Map<String, String>? = null,
    val isResilient: Boolean = false,
    val isContextual: Boolean = false,
    val customDecoder: CustomCoderModel? = null,
    val customEncoder: CustomCoderModel? = null,
    val flattenPath: List<String>? = null,
    val wrapPath: List<String>? = null,
    val wrappedSourceKeys: List<String>? = null,
    val wrappedOmitIfEmpty: Boolean = false,
    val wrappedOmitIfAbsent: List<String> = emptyList(),
    val wrappedUnwrapFields: List<WrappedUnwrapFieldModel> = emptyList(),
    val isInferredSignature: Boolean = false,
    val inferredSubclasses: List<InferredSubclassModel> = emptyList(),
    val isProto: Boolean = false
)
