package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

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
        it.shortName.asString() == C.GHOST_SERIALIZATION
    }
}

/**
 * Checks whether this type declaration is a Kotlin enum class.
 */
internal fun KSType.isEnum(): Boolean {
    return (declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
}

/**
 * Checks whether this type is [kotlin.ByteArray].
 * Fields of this type capture raw JSON bytes via [captureRawJsonBytes].
 */
internal fun KSType.isByteArray(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_BYTE_ARRAY
}

/**
 * Checks whether this type is [com.ghost.serialization.types.RawJson].
 * Fields of this type capture raw JSON bytes via [captureRawJsonBytes].
 */
internal fun KSType.isRawJson(): Boolean {
    return declaration.qualifiedName?.asString() == C.K_RAW_JSON
}

/**
 * Checks whether this type captures opaque JSON inline (ByteArray or RawJson).
 */
internal fun KSType.isOpaqueJson(): Boolean = isByteArray() || isRawJson()
