package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Resolves the generated serializer companion object's [ClassName] for this [KSType].
 * For example: maps type `User` to `com.example.User_Serializer`.
 */
internal fun KSType.serializerClassName(): ClassName {
    val classDeclaration = declaration as KSClassDeclaration
    return classDeclaration.toClassName().serializerClassName()
}

/**
 * Resolves the generated serializer companion object's [ClassName] for this [ClassName].
 * For example: maps `User` to `User_Serializer`.
 */
internal fun ClassName.serializerClassName(): ClassName {
    return ClassName(
        packageName,
        "${simpleNames.joinToString(C.STR_UNDERSCORE)}${C.STR_SERIALIZER_SUFFIX}"
    )
}

/**
 * Resolves a non-nullable representation of this [KSType]'s KotlinPoet TypeName.
 */
private fun KSType.nonNullTypeName() = toTypeName().copy(nullable = false)

/**
 * Checks whether this type matches the standard primitive [Int] type.
 */
internal fun KSType.isPrimitiveInt(): Boolean {
    return nonNullTypeName() == INT
}

/**
 * Checks whether this type matches the standard primitive [Boolean] type.
 */
internal fun KSType.isPrimitiveBoolean(): Boolean {
    return nonNullTypeName() == BOOLEAN
}

/**
 * Checks whether this type matches the standard primitive [Long] type.
 */
internal fun KSType.isPrimitiveLong(): Boolean {
    return nonNullTypeName() == LONG
}

internal fun KSType.isPrimitiveULong(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_ULONG
}

/** Kotlin inline unsigned scalars — not user `@JvmInline` value classes for codegen unboxing. */
internal fun KSType.isKotlinUnsignedPrimitive(): Boolean {
    return when (declaration.qualifiedName?.asString()) {
        C.K_ULONG, C.K_UINT, C.K_USHORT, C.K_UBYTE -> true
        else -> false
    }
}

/**
 * Checks whether this type matches the standard primitive [Double] type.
 */
internal fun KSType.isPrimitiveDouble(): Boolean {
    return nonNullTypeName() == DOUBLE
}

/**
 * Checks whether this type matches the standard primitive [Float] type.
 */
internal fun KSType.isPrimitiveFloat(): Boolean {
    return nonNullTypeName() == FLOAT
}

internal fun KSType.isPrimitiveByte(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_BYTE
}

internal fun KSType.isPrimitiveShort(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_SHORT
}

internal fun KSType.isPrimitiveChar(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_CHAR
}

/**
 * Checks whether this type is a supported standard JVM/Kotlin primitive.
 */
internal fun KSType.isPrimitive(): Boolean {
    return isPrimitiveInt() ||
            isPrimitiveBoolean() ||
            isPrimitiveLong() ||
            isPrimitiveULong() ||
            isPrimitiveDouble() ||
            isPrimitiveFloat() ||
            isPrimitiveByte() ||
            isPrimitiveShort() ||
            isPrimitiveChar()
}

/**
 * Checks whether this type matches the standard [List] type.
 */
internal fun KSType.isList(): Boolean {
    return declaration.qualifiedName?.asString() == C.LIST_QUALIFIED
}

/**
 * Checks whether this type matches the standard [Set] type.
 */
internal fun KSType.isSet(): Boolean {
    return declaration.qualifiedName?.asString() == C.SET_QUALIFIED
}

/**
 * Checks whether this type matches the standard [Map] type.
 */
internal fun KSType.isMap(): Boolean {
    return declaration.qualifiedName?.asString() == C.MAP_QUALIFIED
}

/**
 * Checks whether this type matches the standard [String] type.
 */
internal fun KSType.isString(): Boolean {
    return declaration.qualifiedName?.asString() == C.STRING_QUALIFIED
}

/**
 * Checks whether this type is annotated with `@GhostSerialization` indicating it is a serializable model.
 */
internal fun KSType.isGhost(): Boolean {
    return declaration.annotations.any {
        it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION
    }
}

/**
 * Checks whether this type declaration is a Kotlin enum class.
 */
internal fun KSType.isEnum(): Boolean {
    return (declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
}

/**
 * Checks whether this type's declaration is a Kotlin `value`/`inline` class.
 */
internal fun KSType.isValueClassType(): Boolean {
    val classDeclaration = declaration as? KSClassDeclaration ?: return false
    return classDeclaration.modifiers.contains(Modifier.VALUE) ||
            classDeclaration.modifiers.contains(Modifier.INLINE)
}

/**
 * Resolves the wrapped inner type of a Kotlin `value`/`inline` class, or `null` if this
 * type isn't a value class or has no resolvable primary-constructor parameter.
 */
internal fun KSType.resolveValueClassInnerType(): KSType? {
    val classDeclaration = declaration as? KSClassDeclaration ?: return null
    val primaryConstructor = classDeclaration.primaryConstructor ?: return null
    val param = primaryConstructor.parameters.firstOrNull() ?: return null
    return param.type.resolve()
}

/**
 * Checks whether this type is [kotlin.ByteArray].
 * Fields of this type capture raw JSON bytes via the reader's
 * `captureRawJsonBytes` extension.
 */
internal fun KSType.isByteArray(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_BYTE_ARRAY
}

/**
 * Checks whether this type is `RawJson`.
 * Fields of this type capture raw JSON bytes via the reader's
 * `captureRawJsonBytes` extension.
 */
internal fun KSType.isRawJson(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_RAW_JSON
}

/**
 * True when this type (recursively) uses JSON-only codegen features unsupported on YAML paths.
 */
internal fun KSType.containsYamlIncompatibleType(isProto: Boolean): Boolean {
    if (isRawJson()) return true
    if (!isProto && isByteArray()) return true
    val declaration = this.declaration as? KSClassDeclaration ?: return false
    if (declaration.modifiers.contains(Modifier.SEALED)) return true
    if (declaration.modifiers.contains(Modifier.VALUE) ||
        declaration.modifiers.contains(Modifier.INLINE)
    ) {
        val inner = declaration.primaryConstructor?.parameters?.firstOrNull()?.type?.resolve()
        if (inner != null && inner.containsYamlIncompatibleType(isProto)) return true
    }
    if (isList() || isSet()) {
        val inner = arguments.firstOrNull()?.type?.resolve()
        if (inner != null && inner.containsYamlIncompatibleType(isProto)) return true
    }
    if (isMap()) {
        val value = arguments.getOrNull(1)?.type?.resolve()
        if (value != null && value.containsYamlIncompatibleType(isProto)) return true
    }
    return false
}

/**
 * Converts a Kotlin property name into a generated local-identifier base without underscores.
 * Example: `id_internal` → `idInternal`. Named constructor args keep the original [String].
 */
internal fun String.toGeneratedLocalBase(): String {
    if (C.STR_UNDERSCORE !in this) {
        return this
    }
    return split(C.STR_UNDERSCORE)
        .filter { it.isNotEmpty() }
        .mapIndexed { index, part ->
            if (index == 0) {
                part
            } else {
                part.replaceFirstChar { it.uppercaseChar() }
            }
        }
        .joinToString(C.STR_EMPTY)
}

/** Local tracking variable name: `id` → `idValue`, `id_internal` → `idInternalValue`. */
internal fun GhostPropertyModel.localValueName(): String =
    C.TEMPLATE_VAR_NAME.format(kotlinName.toGeneratedLocalBase())

/** DecodingContext field name mirroring [localValueName] base (no `Value` suffix). */
internal fun GhostPropertyModel.localTrackingName(): String =
    kotlinName.toGeneratedLocalBase()

/** CamelCase suffix for compound locals: `extras` → `Extras`, `id_internal` → `IdInternal`. */
internal fun GhostPropertyModel.localNameSuffix(): String =
    localTrackingName().replaceFirstChar { it.uppercaseChar() }
