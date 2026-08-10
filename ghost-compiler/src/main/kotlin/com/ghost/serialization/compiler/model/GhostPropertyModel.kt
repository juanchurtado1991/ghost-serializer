package com.ghost.serialization.compiler.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.TypeName

/**
 * Main metadata model representing a serializable property.
 *
 * Contains all type resolutions, constraints, annotations, and hierarchical structures
 * resolved during compiler analysis to drive target code generation.
 *
 * @property kotlinName Name of the property as declared in Kotlin code.
 * @property jsonName Target JSON field name (resolves compatibility annotations like GhostName).
 * @property type The KSP [KSType] representation of the property's resolved type.
 * @property typeName KotlinPoet [TypeName] representation of the property's type.
 * @property isNullable True if the property's type is marked nullable.
 * @property isGhost True if the property's type is itself annotated with @GhostSerialization.
 * @property isList True if the property type is a [List].
 * @property isSet True if the property type is a [Set].
 * @property isEnum True if the property type is an enum.
 * @property listInnerType The resolved [KSType] of the generic element inside the list, if applicable.
 * @property listInnerIsGhost True if the list inner type is annotated with @GhostSerialization.
 * @property listInnerIsEnum True if the list inner type is an enum.
 * @property hasDefaultValue True if the property has a parameter default value in the constructor.
 * @property defaultExpression Whitelisted Kotlin source of the ctor default (`"viewer"`, `1`,
 *   `emptyList()`, …), or `null` when unavailable / unsafe — callers must fall back to `.copy()`.
 * @property isMap True if the property type is a [Map].
 * @property mapValueType The resolved [KSType] of the map's value parameter, if applicable.
 * @property mapValueIsGhost True if the map's value type is annotated with @GhostSerialization.
 * @property isPrimitiveArray True if the type represents a primitive array (e.g. IntArray, LongArray).
 * @property primitiveArrayType String suffix/type representing the primitive array element type.
 * @property isValueClass True if the type is declared as an inline or value class.
 * @property valueClassProperty Analyzed property model of the underlying value class property, if applicable.
 * @property isSealedClass True if the type represents a sealed class hierarchy.
 * @property sealedSubclasses List of declarations for all direct subclasses of the sealed class.
 * @property enumValues Map of enum entry names to their customized JSON serialization names.
 * @property isResilient True if this property or class is marked with @GhostResilient.
 * @property isContextual True if the type relies on contextual serialization (e.g., third party classes).
 * @property customDecoder Provider configuration for a custom decoder annotated on this property.
 * @property customEncoder Provider configuration for a custom encoder annotated on this property.
 * @property flattenPath Nested JSON path list if this property is flattened using @GhostFlatten.
 * @property wrapPath Nested JSON path list if this property is wrapped using @GhostWrap.
 * @property wrappedSourceKeys Wire keys collapsed into this property via @GhostWrappedKeys.
 * @property wrappedOmitIfEmpty When true, absent/null-only captures yield a null wrapper property.
 * @property wrappedOmitIfAbsent Keys that force a null wrapper when absent or JSON null.
 * @property wrappedUnwrapFields Ordered wire fields used to unwrap this property during serialization.
 * @property isInferredSignature True if this property serves as an inferred signature candidate.
 * @property inferredSubclasses Inferred sealed subclasses associated with this property model.
 * @property isProto True if the enclosing class is annotated with `@GhostProtoSerialization`.
 *   Applies proto3 JSON mapping rules to this property: 64-bit integer (`Long`) fields are
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
