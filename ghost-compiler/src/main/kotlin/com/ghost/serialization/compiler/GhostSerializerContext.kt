package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Immutable metadata for generating a single [GhostSerializer] companion object.
 */
internal class GhostSerializerContext private constructor(
    val properties: List<GhostPropertyModel>,
    val classDeclaration: KSClassDeclaration,
    val textChannel: Boolean,
    val envelopeModel: GhostEnvelopeModel?,
    val fullPaths: List<List<String>>,
    val isSealed: Boolean,
    val isValue: Boolean,
    val isEnum: Boolean,
    val isObject: Boolean,
    val isResilient: Boolean,
    val isInferred: Boolean,
    val sealedSubclasses: List<KSClassDeclaration>,
    val packageName: String,
    val originalClassName: ClassName,
    val baseClassName: String,
    val finalTypeName: String,
    val discriminator: String?,
    val sealedDiscriminatorKey: String,
    val enumValues: Map<String, String>?,
    val hasFallbackEnum: Boolean,
    val serializerInterface: ClassName,
    val streamingWriterClass: ClassName,
    val flatWriterClass: ClassName,
    val stringWriterClass: ClassName,
    val streamingReaderClass: ClassName,
    val flatReaderClass: ClassName,
    val stringReaderClass: ClassName,
) {
    val readerClass: ClassName = streamingReaderClass

    val serializerName: String = baseClassName + C.STR_SERIALIZER_SUFFIX

    fun needsObjectParsingImports(): Boolean {
        if (isEnum || isValue) return false
        if (isObject) return true
        if (isSealed) return isInferred
        return properties.isNotEmpty()
    }

    fun needsCachedByteStringHeaders(): Boolean {
        if (isEnum || isValue || isSealed || isObject) return false
        return getAllJsonNames().isNotEmpty()
    }

    fun getAllJsonNames(): List<String> {
        val names = mutableSetOf<String>()
        val allProps = if (isSealed && isInferred) {
            properties.firstOrNull()?.inferredSubclasses?.flatMap { it.properties } ?: emptyList()
        } else {
            properties
        }
        allProps.forEach { prop ->
            if (prop.flattenPath != null) {
                names.addAll(prop.flattenPath)
            } else if (prop.wrapPath != null) {
                names.addAll(prop.wrapPath)
            }
            names.add(prop.jsonName)
        }
        return names.toList().sorted()
    }

    companion object {
        fun from(
            properties: List<GhostPropertyModel>,
            classDeclaration: KSClassDeclaration,
            textChannel: Boolean,
            envelopeModel: GhostEnvelopeModel?,
        ): GhostSerializerContext {
            val fullPaths = properties.map {
                it.flattenPath
                    ?: (it.wrapPath?.let { path -> path + it.jsonName }
                        ?: listOf(it.jsonName))
            }

            val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
            val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
                classDeclaration.modifiers.contains(Modifier.INLINE)
            val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS
            val isObject = classDeclaration.classKind == ClassKind.OBJECT
            val isResilient =
                classDeclaration.annotations.any { it.shortName.asString() == C.GHOST_RESILIENT }

            val sealedSubclasses = if (isSealed) {
                classDeclaration.getSealedSubclasses().toList()
            } else {
                emptyList()
            }

            val originalClassName = classDeclaration.toClassName()
            val baseClassName = originalClassName.simpleNames.joinToString(C.STR_UNDERSCORE)

            val parentSealedClass = resolveParentSealedClass(
                classDeclaration,
                isSealed,
                isValue,
                isEnum
            )

            val discriminator = if (parentSealedClass != null) {
                val customName = classDeclaration.annotations
                    .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
                    ?.arguments?.find { it.name?.asString() == C.ARG_NAME }?.value as? String
                if (!customName.isNullOrEmpty()) customName else classDeclaration.simpleName.asString()
            } else {
                null
            }

            val sealedDiscriminatorKey = run {
                val annotationSource = parentSealedClass ?: classDeclaration
                annotationSource.annotations
                    .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
                    ?.arguments
                    ?.find { it.name?.asString() == C.ARG_DISCRIMINATOR }
                    ?.value as? String
                    ?: C.STR_DEFAULT_DISCRIMINATOR
            }

            val customTypeName = classDeclaration.annotations
                .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
                ?.arguments
                ?.find { it.name?.asString() == C.ARG_NAME }
                ?.value as? String ?: C.STR_EMPTY

            val isInferred = (if (discriminator != null) {
                classDeclaration.parentDeclaration as? KSClassDeclaration
            } else {
                classDeclaration
            })?.annotations
                ?.find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
                ?.arguments
                ?.find { it.name?.asString() == C.ARG_INFERRED }
                ?.value as? Boolean
                ?: false

            val finalTypeName = customTypeName.ifEmpty { classDeclaration.simpleName.asString() }
            val enumValues = properties.firstOrNull { it.isEnum }?.enumValues
            val hasFallbackEnum = classDeclaration.annotations.any {
                it.shortName.asString() == C.STR_FALLBACK_ANNOTATION
            }

            return GhostSerializerContext(
                properties = properties,
                classDeclaration = classDeclaration,
                textChannel = textChannel,
                envelopeModel = envelopeModel,
                fullPaths = fullPaths,
                isSealed = isSealed,
                isValue = isValue,
                isEnum = isEnum,
                isObject = isObject,
                isResilient = isResilient,
                isInferred = isInferred,
                sealedSubclasses = sealedSubclasses,
                packageName = classDeclaration.packageName.asString(),
                originalClassName = originalClassName,
                baseClassName = baseClassName,
                finalTypeName = finalTypeName,
                discriminator = discriminator,
                sealedDiscriminatorKey = sealedDiscriminatorKey,
                enumValues = enumValues,
                hasFallbackEnum = hasFallbackEnum,
                serializerInterface = ClassName(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER),
                streamingWriterClass = ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_WRITER),
                flatWriterClass = ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_FLAT_WRITER),
                stringWriterClass = ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_STRING_WRITER),
                streamingReaderClass = ClassName(C.PKG_PARSER, C.STR_GHOST_JSON_READER),
                flatReaderClass = ClassName(C.PKG_PARSER, C.STR_GHOST_JSON_FLAT_READER),
                stringReaderClass = ClassName(C.PKG_PARSER, C.STR_GHOST_JSON_STRING_READER),
            )
        }

        private fun resolveParentSealedClass(
            classDeclaration: KSClassDeclaration,
            isSealed: Boolean,
            isValue: Boolean,
            isEnum: Boolean,
        ): KSClassDeclaration? {
            if (isSealed || isValue || isEnum) return null
            val parentDecl = classDeclaration.parentDeclaration as? KSClassDeclaration
            if (parentDecl != null && parentDecl.modifiers.contains(Modifier.SEALED)) {
                return parentDecl
            }
            for (superType in classDeclaration.superTypes) {
                val resolved = superType.resolve().declaration as? KSClassDeclaration
                if (resolved != null && resolved.modifiers.contains(Modifier.SEALED)) {
                    return resolved
                }
            }
            return null
        }
    }
}
