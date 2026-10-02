package com.ghost.serialization.compiler.analysis

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC

/**
 * Stateless type/annotation inspection shared by [GhostAnalyzer], [WrappedKeysAnalyzer], and
 * [CustomCoderAnalyzer] — none of these need a [com.google.devtools.ksp.processing.KSPLogger], so
 * they live as plain top-level functions instead of being duplicated across those 3 classes.
 */

internal fun getJsonName(prop: KSPropertyDeclaration): String = getSerialName(declaration = prop)

/** Resolves the serialized name, preferring `@GhostName` then kotlinx `@SerialName`. */
internal fun getSerialName(declaration: KSAnnotated): String {
    val annotations = declaration.annotations.toList()

    // 1. GhostName (Primary)
    val ghostName = annotations.find { it.shortName.asString() == AC.GHOST_NAME }
    if (ghostName != null) {
        val arg = ghostName.arguments.find { it.name?.asString() == AC.NAME }
            ?: ghostName.arguments.firstOrNull()
        return arg?.value?.toString() ?: CC.STR_EMPTY
    }

    // 2. SerialName (kotlinx compatibility)
    val serialName = annotations.find {
        val name = it.shortName.asString()
        name == AC.SERIAL_NAME || name.endsWith(AC.STR_SERIAL_NAME_SUFFIX)
    }

    if (serialName != null) {
        val arg = serialName.arguments.find { it.name?.asString() == AC.STR_VALUE_ARG }
            ?: serialName.arguments.firstOrNull()

        return arg?.value?.toString()
            ?: (declaration as? KSDeclaration)?.simpleName?.asString()
            ?: CC.STR_EMPTY
    }

    return (declaration as? KSDeclaration)
        ?.simpleName?.asString()
        ?: CC.STR_EMPTY
}

internal fun KSPropertyDeclaration.hasAnnotation(name: String): Boolean {
    return annotations.any { it.shortName.asString() == name }
}

internal fun isEnumType(type: KSType): Boolean =
    (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

internal fun isGhostType(type: KSType): Boolean =
    type.declaration.annotations.any {
        val name = it.shortName.asString()
        name == CC.ANNOTATION_GHOST_SERIALIZATION || name == CC.ANNOTATION_GHOST_PROTO_SERIALIZATION
    }

internal fun isSealedClass(type: KSType): Boolean {
    val declaration = type.declaration as? KSClassDeclaration ?: return false
    return declaration.modifiers.contains(Modifier.SEALED)
}

internal fun isValueClass(type: KSType): Boolean {
    val declaration = type.declaration as? KSClassDeclaration ?: return false
    return declaration.modifiers.contains(Modifier.VALUE) ||
            declaration.modifiers.contains(Modifier.INLINE)
}

internal fun resolveFirstTypeArg(type: KSType): KSType? {
    return type.arguments.firstOrNull()?.type?.resolve()
}

internal fun resolveSecondTypeArg(type: KSType): KSType? {
    return type.arguments.getOrNull(1)?.type?.resolve()
}

internal fun toLowerCamelCase(str: String): String {
    if (str.isEmpty()) return str
    val sb = StringBuilder()
    var uppercaseNext = false
    var i = 0
    val len = str.length
    while (i < len) {
        val c = str[i]
        if (c == AC.CHAR_UNDERSCORE) {
            uppercaseNext = true
        } else {
            if (uppercaseNext) {
                sb.append(c.uppercaseChar())
                uppercaseNext = false
            } else {
                if (i == 0) {
                    sb.append(c.lowercaseChar())
                } else {
                    sb.append(c)
                }
            }
        }
        i++
    }
    return sb.toString()
}
