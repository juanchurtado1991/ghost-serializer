package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.annotations.GhostWrappedKeys
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.ghost.serialization.compiler.model.WrappedUnwrapFieldModel
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC

/**
 * Resolves and validates `@GhostWrappedKeys`: collapsing several wire keys into one Kotlin
 * property, and the reverse mapping an emitter needs to read each collapsed key back out of a
 * nested/sealed type. Split out of [GhostAnalyzer] since this changes for a different reason —
 * the wrapped-keys wire format — than class validation or sealed-subclass resolution does.
 */
@OptIn(KspExperimental::class)
internal class WrappedKeysAnalyzer(private val logger: KSPLogger) {

    fun resolveWrappedKeysAnnotation(prop: KSPropertyDeclaration): WrappedKeysConfig? {
        resolveWrappedKeysFromAnnotated(annotated = prop)?.let { return it }

        val classDecl = prop.parentDeclaration as? KSClassDeclaration
        val parameter = classDecl?.primaryConstructor?.parameters?.firstOrNull {
            it.name?.asString() == prop.simpleName.asString()
        }
        parameter?.let { resolveWrappedKeysFromAnnotated(annotated = it) }?.let { return it }

        return null
    }

    fun resolveWrappedUnwrapFields(
        type: KSType,
        wrapperPath: List<String>,
        sourceKeys: List<String>,
    ): List<WrappedUnwrapFieldModel> {
        val declaration = type.declaration as? KSClassDeclaration ?: return emptyList()
        if (!isGhostType(type = type)) {
            return emptyList()
        }

        val properties = declaration.getAllProperties()
            .filterNot { it.hasAnnotation(name = AC.GHOST_IGNORE) }
            .toList()

        return sourceKeys.mapNotNull { wireKey ->
            resolveUnwrapFieldForWireKey(
                properties = properties,
                wrapperPath = wrapperPath,
                wireKey = wireKey,
                ownerType = type
            )
        }
    }

    fun validateWrappedKeys(
        properties: List<GhostPropertyModel>,
        clazz: KSClassDeclaration,
    ) {
        val wireKeys = mutableMapOf<String, String>()
        properties.forEach { prop ->
            prop.wrappedKeys?.sourceKeys?.forEach { key ->
                val owner = wireKeys.put(key, prop.kotlinName)
                if (owner != null && owner != prop.kotlinName) {
                    logger.error(
                        AC.STR_ERR_WRAPPED_DUP_KEY_1 + key + AC.STR_ERR_WRAPPED_DUP_KEY_2 +
                                clazz.simpleName.asString() + AC.STR_ERR_WRAPPED_DUP_KEY_3 + owner +
                                AC.STR_ERR_WRAPPED_DUP_KEY_4 + prop.kotlinName + AC.STR_ERR_WRAPPED_DUP_KEY_5,
                        clazz,
                    )
                }
            }
            if (prop.wrappedKeys?.omitIfEmpty == true && !prop.isNullable) {
                logger.error(
                    AC.STR_ERR_WRAPPED_OMIT_IF_EMPTY_1 + prop.kotlinName + AC.STR_ERR_WRAPPED_OMIT_IF_EMPTY_2,
                    clazz,
                )
            }
            if (prop.flattenPath != null || prop.wrapPath != null) {
                if (prop.wrappedKeys != null) {
                    logger.error(
                        AC.STR_ERR_WRAPPED_COMBINE_1 + prop.kotlinName + AC.STR_ERR_WRAPPED_COMBINE_2,
                        clazz,
                    )
                }
            }
        }
        properties.forEach { prop ->
            if (prop.wrappedKeys == null) {
                return@forEach
            }
            prop.wrappedKeys.sourceKeys.forEach { key ->
                if (properties.any { it.wrappedKeys == null && it.jsonName == key }) {
                    logger.error(
                        AC.STR_ERR_WRAPPED_KEY_CONFLICT_1 + key + AC.STR_ERR_WRAPPED_KEY_CONFLICT_2 +
                                clazz.simpleName.asString() + AC.STR_ERR_WRAPPED_KEY_CONFLICT_3,
                        clazz,
                    )
                }
            }
        }
    }

    private fun KSAnnotation.readBooleanArgument(argName: String): Boolean {
        return arguments.find { it.name?.asString() == argName }?.value as? Boolean ?: false
    }

    private fun KSAnnotation.readStringArrayArgument(argName: String): List<String> {
        val value = arguments.find { it.name?.asString() == argName }?.value ?: return emptyList()
        return when (value) {
            is Array<*> -> value.filterIsInstance<String>()
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun resolveUnwrapFieldForWireKey(
        properties: List<KSPropertyDeclaration>,
        wrapperPath: List<String>,
        wireKey: String,
        ownerType: KSType,
    ): WrappedUnwrapFieldModel? {
        val direct = properties.find { getJsonName(prop = it) == wireKey }
        if (direct != null) {
            val directType = direct.type.resolve()
            return WrappedUnwrapFieldModel(
                jsonName = wireKey,
                kotlinPath = wrapperPath + direct.simpleName.asString(),
                isNullable = directType.isMarkedNullable,
                typeName = directType.toTypeName(),
                type = directType,
            )
        }

        for (property in properties) {
            val nestedConfig = resolveWrappedKeysAnnotation(prop = property) ?: continue
            if (wireKey !in nestedConfig.keys) {
                continue
            }
            val nestedType = property.type.resolve().makeNotNullable()
            val nestedPath = wrapperPath + property.simpleName.asString()
            val nestedDecl = nestedType.declaration as? KSClassDeclaration ?: continue
            val nestedProps = nestedDecl.getAllProperties()
                .filterNot { it.hasAnnotation(name = AC.GHOST_IGNORE) }
                .toList()
            val leaf = resolveUnwrapFieldForWireKey(
                properties = nestedProps,
                wrapperPath = nestedPath,
                wireKey = wireKey,
                ownerType = nestedType
            )
            if (leaf != null) {
                return leaf
            }
        }

        resolveUnwrapFieldFromSealedSubclass(
            wrapperPath = wrapperPath,
            wireKey = wireKey,
            ownerType = ownerType
        )?.let { return it }

        logger.warn(
            AC.STR_WARN_WRAPPED_UNMAPPED_1 + wireKey + AC.STR_WARN_WRAPPED_UNMAPPED_2 +
                    ownerType.declaration.simpleName.asString(),
        )
        return null
    }

    /**
     * proto3 `oneof` support: when the wrapped type is sealed, wire keys live on its subclasses
     * rather than the parent. Resolves the key against each subclass and tags the result with it
     * so the emitter can smart-cast before accessing it.
     */
    private fun resolveUnwrapFieldFromSealedSubclass(
        wrapperPath: List<String>,
        wireKey: String,
        ownerType: KSType,
    ): WrappedUnwrapFieldModel? {
        val ownerDecl = ownerType.declaration as? KSClassDeclaration ?: return null
        if (!ownerDecl.modifiers.contains(Modifier.SEALED)) {
            return null
        }

        for (subclass in ownerDecl.getSealedSubclasses()) {
            val subclassProps = subclass.getAllProperties()
                .filterNot { it.hasAnnotation(name = AC.GHOST_IGNORE) }
                .toList()
            val direct = subclassProps.find { getJsonName(prop = it) == wireKey } ?: continue
            val directType = direct.type.resolve()
            return WrappedUnwrapFieldModel(
                jsonName = wireKey,
                kotlinPath = wrapperPath + direct.simpleName.asString(),
                isNullable = directType.isMarkedNullable,
                typeName = directType.toTypeName(),
                type = directType,
                sealedSubclassName = subclass.toClassName(),
            )
        }
        return null
    }

    private fun resolveWrappedKeysFromAnnotated(annotated: KSAnnotated): WrappedKeysConfig? {
        try {
            when (annotated) {
                is KSPropertyDeclaration -> {
                    annotated.getAnnotationsByType(GhostWrappedKeys::class)
                        .firstOrNull()
                        ?.let { wrappedKeysConfigFromAnnotation(annotation = it, source = annotated) }
                        ?.let { return it }
                }

                is KSValueParameter -> {
                    annotated.getAnnotationsByType(GhostWrappedKeys::class)
                        .firstOrNull()
                        ?.let { wrappedKeysConfigFromAnnotation(annotation = it, source = annotated) }
                        ?.let { return it }
                }
            }
        } catch (_: Exception) {
            // kspCommonMainKotlinMetadata may throw on getAnnotationsByType for array args.
        }

        return annotated.annotations
            .find { it.shortName.asString() == AC.GHOST_WRAPPED_KEYS }
            ?.let { wrappedKeysConfigFromKsAnnotation(annotation = it, source = annotated) }
    }

    private fun wrappedKeysConfigFromAnnotation(
        annotation: GhostWrappedKeys,
        source: KSAnnotated,
    ): WrappedKeysConfig {
        return wrappedKeysConfigFromValues(
            keys = annotation.keys.toList(),
            omitIfEmpty = annotation.omitIfEmpty,
            omitIfAbsent = annotation.omitIfAbsent.toList(),
            source = source,
        )
    }

    private fun wrappedKeysConfigFromKsAnnotation(
        annotation: KSAnnotation,
        source: KSAnnotated,
    ): WrappedKeysConfig {
        return wrappedKeysConfigFromValues(
            keys = annotation.readStringArrayArgument(argName = AC.KEYS_ARG),
            omitIfEmpty = annotation.readBooleanArgument(argName = AC.OMIT_IF_EMPTY_ARG),
            omitIfAbsent = annotation.readStringArrayArgument(argName = AC.OMIT_IF_ABSENT_ARG),
            source = source,
        )
    }

    private fun wrappedKeysConfigFromValues(
        keys: List<String>,
        omitIfEmpty: Boolean,
        omitIfAbsent: List<String>,
        source: KSAnnotated,
    ): WrappedKeysConfig {
        if (keys.isEmpty()) {
            val name = when (source) {
                is KSPropertyDeclaration -> source.simpleName.asString()
                is KSValueParameter -> source.name?.asString() ?: CC.STR_EMPTY
                else -> CC.STR_EMPTY
            }
            logger.error(
                AC.STR_ERR_WRAPPED_EMPTY_KEYS_1 + name + AC.STR_ERR_WRAPPED_EMPTY_KEYS_2,
                source,
            )
            return WrappedKeysConfig(keys = emptyList(), omitIfEmpty = false, omitIfAbsent = emptyList())
        }
        return WrappedKeysConfig(
            keys = keys,
            omitIfEmpty = omitIfEmpty,
            omitIfAbsent = omitIfAbsent,
        )
    }
}

internal data class WrappedKeysConfig(
    val keys: List<String>,
    val omitIfEmpty: Boolean,
    val omitIfAbsent: List<String>,
)
