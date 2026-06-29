package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
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
 * Recursively resolves typealias chains to the underlying concrete type.
 *
 * KSP reports a field whose declared type is a `typealias` with a [KSType] whose
 * [KSType.declaration] is a [KSTypeAlias], not the aliased class. Without resolution,
 * `isMap()`, `isList()`, `isString()`, and friends all compare the wrong `qualifiedName`
 * and silently fall through to `Ghost.getSerializer(Alias::class)!!` → `null` → NPE at
 * class-initialization time.
 *
 * Example: `typealias AttributeStateMap = Map<String, AttributeState>`
 *   Without this function: `isMap()` → `false` → serializer NPE
 *   With this function:    `isMap()` → `true`  → `MapSerializer` generated correctly
 */
internal fun KSType.resolveAliases(): KSType {
    val decl = declaration
    return if (decl is KSTypeAlias) decl.type.resolve().resolveAliases() else this
}

/**
 * Resolves the generated serializer companion object's [ClassName] for this [KSType].
 * For example: maps type `User` to `com.example.User_Serializer`.
 */
internal fun KSType.serializerClassName(): ClassName {
    val classDeclaration = resolveAliases().declaration as KSClassDeclaration
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
 * Resolves a non-nullable representation of this [KSType]'s KotlinPoet TypeName,
 * after expanding any typealias chain.
 */
private fun KSType.nonNullTypeName() = resolveAliases().toTypeName().copy(nullable = false)

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

/**
 * Checks whether this type is a supported standard JVM/Kotlin primitive.
 */
internal fun KSType.isPrimitive(): Boolean {
    return isPrimitiveInt() ||
        isPrimitiveBoolean() ||
        isPrimitiveLong() ||
        isPrimitiveDouble() ||
        isPrimitiveFloat()
}

/**
 * Checks whether this type matches the standard [List] type.
 */
internal fun KSType.isList(): Boolean {
    return resolveAliases().declaration.qualifiedName?.asString() == C.LIST_QUALIFIED
}

/**
 * Checks whether this type matches the standard [Map] type.
 */
internal fun KSType.isMap(): Boolean {
    return resolveAliases().declaration.qualifiedName?.asString() == C.MAP_QUALIFIED
}

/**
 * Checks whether this type matches the standard [String] type.
 */
internal fun KSType.isString(): Boolean {
    return resolveAliases().declaration.qualifiedName?.asString() == C.STRING_QUALIFIED
}

/**
 * Checks whether this type is annotated with `@GhostSerialization` indicating it is a serializable model.
 */
internal fun KSType.isGhost(): Boolean {
    return resolveAliases().declaration.annotations.any {
        it.shortName.asString() == C.GHOST_SERIALIZATION
    }
}

/**
 * Checks whether this type declaration is a Kotlin enum class.
 */
internal fun KSType.isEnum(): Boolean {
    return (resolveAliases().declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
}

/**
 * Checks whether this type is [kotlin.ByteArray].
 * Fields of this type capture raw JSON bytes via [captureRawJsonBytes].
 */
internal fun KSType.isByteArray(): Boolean {
    return resolveAliases().declaration.qualifiedName?.asString() == C.K_BYTE_ARRAY
}
