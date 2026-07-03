@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C
import com.ghost.serialization.annotations.GhostWrappedKeys

/**
 * Analyzes Kotlin classes during KSP processing to generate serialization metadata.
 *
 * This analyzer is responsible for inspecting a [KSClassDeclaration], validating it against
 * the framework's serialization rules, and converting its valid properties into a list of
 * [GhostPropertyModel] instances for code generation.
 *
 * ### Validations:
 * - **Supported Types:** The target must be a `data class`, `sealed class`, `value class`, or `enum class`.
 * - **Visibility:** Properties cannot be `private`.
 * - **Maps:** If a property is a `Map`, its key must resolve to a `String`.
 * - **Naming:** Duplicate JSON keys within the same class are not allowed.
 *
 * @property logger The [KSPLogger] used to report compilation errors for invalid declarations.
 */
internal class GhostAnalyzer(private val logger: KSPLogger) {

    /**
     * Analyzes the given class declaration and resolves its properties to a list of models.
     *
     * @param classDeclaration The class metadata declaration to analyze.
     * @return List of parsed [GhostPropertyModel] configurations.
     */
    fun analyze(classDeclaration: KSClassDeclaration): List<GhostPropertyModel> {
        val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
        val isData = classDeclaration.modifiers.contains(Modifier.DATA)
        val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
                classDeclaration.modifiers.contains(Modifier.INLINE)
        val isObject = classDeclaration.classKind == ClassKind.OBJECT

        val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS

        if (isObject) return emptyList()

        validateClassKind(classDeclaration, isData, isSealed, isValue, isEnum)

        val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()

        val allProps = classDeclaration.getAllProperties().toList()
        val overriddenProps = allProps.mapNotNull { it.findOverridee() }.toSet()
        val properties = allProps
            .filterNot { it in overriddenProps }
            .filterNot { it.hasAnnotation(C.GHOST_IGNORE) }
            .toList()

        validatePropertyVisibility(classDeclaration, properties)

        val enumValues = getEnumValues(classDeclaration, isEnum)
        val propertyModels = resolvePropertyModels(classDeclaration, properties, parameters, isEnum, enumValues)

        val finalModels = resolveSealedSubclasses(classDeclaration, propertyModels, isSealed)

        validateNames(finalModels, classDeclaration)
        validateWrappedKeys(finalModels, classDeclaration)
        return finalModels
    }

    /**
     * Validates that the class kind is supported by the Ghost framework.
     */
    private fun validateClassKind(
        classDeclaration: KSClassDeclaration,
        isData: Boolean,
        isSealed: Boolean,
        isValue: Boolean,
        isEnum: Boolean
    ) {
        if (!isData && !isSealed && !isValue && !isEnum) {
            logger.error(
                C.STR_ERR_CLASS_1 +
                        "${C.STR_ERR_CLASS_2}${
                            classDeclaration
                                .simpleName
                                .asString()
                        }${C.STR_ERR_CLASS_3}",
                classDeclaration
            )
        }
    }

    /**
     * Validates that none of the serialization properties are declared as private.
     */
    private fun validatePropertyVisibility(
        classDeclaration: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>
    ) {
        val hasPrivateProperties = properties.any {
            it.modifiers.contains(Modifier.PRIVATE)
        }
        if (hasPrivateProperties) {
            logger.error(
                C.STR_ERR_PRIV_1 +
                        "${C.STR_ERR_PRIV_2}${
                            classDeclaration
                                .simpleName
                                .asString()
                        }${C.STR_ERR_PRIV_3}",
                classDeclaration
            )
        }
    }

    /**
     * Resolves the list of property models for standard or enum DTO classes.
     */
    private fun resolvePropertyModels(
        classDeclaration: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>,
        parameters: List<com.google.devtools.ksp.symbol.KSValueParameter>,
        isEnum: Boolean,
        enumValues: Map<String, String>?
    ): List<GhostPropertyModel> {
        return if (isEnum) {
            listOf(
                GhostPropertyModel(
                    kotlinName = C.NAME,
                    jsonName = C.NAME,
                    type = classDeclaration.asType(emptyList()),
                    typeName = classDeclaration.toClassName(),
                    isNullable = false,
                    isGhost = false,
                    isList = false,
                    isSet = false,
                    isEnum = true,
                    enumValues = enumValues
                )
            )
        } else {
            properties.map { prop -> buildPropertyModel(prop, parameters) }
        }
    }

    /**
     * Inspects the sealed subclass hierarchies and recursively builds inferred subclass metadata.
     */
    private fun resolveSealedSubclasses(
        classDeclaration: KSClassDeclaration,
        propertyModels: List<GhostPropertyModel>,
        isSealed: Boolean
    ): List<GhostPropertyModel> {
        return if (isSealed) {
            val inferredSubclasses = classDeclaration
                .getSealedSubclasses()
                .map { subclass ->
                    InferredSubclassModel(
                        subclass,
                        analyze(subclass)
                    )
                }
                .toList()
            propertyModels.map { it.copy(inferredSubclasses = inferredSubclasses) }
                .ifEmpty {
                    listOf(
                        GhostPropertyModel(
                            kotlinName = C.STR_EMPTY, jsonName = C.STR_EMPTY,
                            type = classDeclaration.asType(emptyList()),
                            typeName = classDeclaration.toClassName(),
                            isNullable = false, isGhost = false, isList = false, isSet = false, isEnum = false,
                            inferredSubclasses = inferredSubclasses
                        )
                    )
                }
        } else {
            propertyModels
        }
    }

    private fun getEnumValues(
        classDeclaration: KSClassDeclaration,
        isEnum: Boolean
    ): Map<String, String>? {
        return if (isEnum) {
            classDeclaration.declarations
                .filter { it is KSClassDeclaration && it.classKind == ClassKind.ENUM_ENTRY }
                .map { it as KSClassDeclaration }
                .associate { entry -> entry.simpleName.asString() to getSerialName(entry) }
        } else null
    }

    private fun validateWrappedKeys(
        properties: List<GhostPropertyModel>,
        clazz: KSClassDeclaration,
    ) {
        val wireKeys = mutableMapOf<String, String>()
        properties.forEach { prop ->
            prop.wrappedSourceKeys?.forEach { key ->
                val owner = wireKeys.put(key, prop.kotlinName)
                if (owner != null && owner != prop.kotlinName) {
                    logger.error(
                        C.STR_ERR_WRAPPED_DUP_KEY_1 + key + C.STR_ERR_WRAPPED_DUP_KEY_2 +
                            clazz.simpleName.asString() + C.STR_ERR_WRAPPED_DUP_KEY_3 + owner +
                            C.STR_ERR_WRAPPED_DUP_KEY_4 + prop.kotlinName + C.STR_ERR_WRAPPED_DUP_KEY_5,
                        clazz,
                    )
                }
            }
            if (prop.wrappedOmitIfEmpty && !prop.isNullable) {
                logger.error(
                    C.STR_ERR_WRAPPED_OMIT_IF_EMPTY_1 + prop.kotlinName + C.STR_ERR_WRAPPED_OMIT_IF_EMPTY_2,
                    clazz,
                )
            }
            if (prop.flattenPath != null || prop.wrapPath != null) {
                if (prop.wrappedSourceKeys != null) {
                    logger.error(
                        C.STR_ERR_WRAPPED_COMBINE_1 + prop.kotlinName + C.STR_ERR_WRAPPED_COMBINE_2,
                        clazz,
                    )
                }
            }
        }
        properties.forEach { prop ->
            if (prop.wrappedSourceKeys == null) {
                return@forEach
            }
            prop.wrappedSourceKeys.forEach { key ->
                if (properties.any { it.wrappedSourceKeys == null && it.jsonName == key }) {
                    logger.error(
                        C.STR_ERR_WRAPPED_KEY_CONFLICT_1 + key + C.STR_ERR_WRAPPED_KEY_CONFLICT_2 +
                            clazz.simpleName.asString() + C.STR_ERR_WRAPPED_KEY_CONFLICT_3,
                        clazz,
                    )
                }
            }
        }
    }

    private fun validateNames(
        properties: List<GhostPropertyModel>,
        clazz: KSClassDeclaration
    ) {
        val names = properties.groupBy { it.jsonName }
        names.forEach { (name, props) ->
            if (props.size > C.VAL_ONE) {
                logger.error(
                    "${C.STR_ERR_DUP_1}$name${C.STR_ERR_DUP_2}${
                        clazz
                            .simpleName
                            .asString()
                    }${C.STR_ERR_DUP_3}" +
                            "${C.STR_ERR_DUP_4}${props.joinToString { it.kotlinName }}",
                    clazz
                )
            }
        }
    }

    private fun buildPropertyModel(
        prop: KSPropertyDeclaration,
        parameters: List<com.google.devtools.ksp.symbol.KSValueParameter>
    ): GhostPropertyModel {
        val type = prop.type.resolve()
        val qualifiedName = type.declaration.qualifiedName?.asString()

        val isList = qualifiedName == C.LIST_QUALIFIED
        val isSet = qualifiedName == C.SET_QUALIFIED
        val isMap = qualifiedName == C.MAP_QUALIFIED

        val innerType = if (isList || isSet) {
            resolveFirstTypeArg(type)
        } else {
            null
        }
        val mapKeyType = if (isMap) {
            resolveFirstTypeArg(type)
        } else {
            null
        }
        val mapValueType = if (isMap) {
            resolveSecondTypeArg(type)
        } else {
            null
        }

        validateMapKey(prop, isMap, mapKeyType)

        val param = parameters.find {
            it.name?.asString() == prop.simpleName.asString()
        }

        val isPrimitiveArray = qualifiedName in PRIMITIVE_ARRAYS
        val primitiveArrayType =
            if (isPrimitiveArray) {
                qualifiedName?.removePrefix(C.STR_KOTLIN_DOT)
            } else {
                null
            }

        val customDecoder = resolveCustomCoder(prop, C.GHOST_DECODER)
        val customEncoder = resolveCustomCoder(prop, C.GHOST_ENCODER)

        val flattenPath = resolvePathAnnotation(prop, C.GHOST_FLATTEN)
        val wrapPath = resolvePathAnnotation(prop, C.GHOST_WRAP)
        val wrappedKeysConfig = resolveWrappedKeysAnnotation(prop)

        warnIfCustomCoder(prop.simpleName.asString(), customDecoder, customEncoder)

        val jsonName = flattenPath?.last() ?: getJsonName(prop)
        val wrappedUnwrapFields = if (wrappedKeysConfig != null) {
            resolveWrappedUnwrapFields(
                type = type.makeNotNullable(),
                wrapperPath = emptyList(),
                sourceKeys = wrappedKeysConfig.keys,
            )
        } else {
            emptyList()
        }

        return GhostPropertyModel(
            kotlinName = prop.simpleName.asString(),
            jsonName = jsonName,
            type = type,
            typeName = type.toTypeName(),
            isNullable = type.isMarkedNullable,
            isGhost = isGhostType(type),
            isList = isList,
            isSet = isSet,
            listInnerType = innerType,
            isEnum = isEnumType(type),
            listInnerIsGhost = innerType?.let { isGhostType(it) } ?: false,
            listInnerIsEnum = innerType?.let { isEnumType(it) } ?: false,
            hasDefaultValue = param?.hasDefault ?: false,
            isInConstructor = param != null,
            isMap = isMap,
            mapValueType = mapValueType,
            mapValueIsGhost = mapValueType?.let { isGhostType(it) } ?: false,
            isPrimitiveArray = isPrimitiveArray,
            primitiveArrayType = primitiveArrayType,
            isValueClass = isValueClass(type),
            valueClassProperty = if (isValueClass(type)) {
                resolveValueClassProperty(type)
            } else {
                null
            },
            isSealedClass = isSealedClass(type),
            sealedSubclasses = resolveSealedSubclassesForType(type),
            isResilient = isResilientProperty(prop),
            isContextual = isContextualType(type, isList, isSet, isMap, isPrimitiveArray),
            customDecoder = customDecoder,
            customEncoder = customEncoder,
            flattenPath = flattenPath,
            wrapPath = wrapPath,
            wrappedSourceKeys = wrappedKeysConfig?.keys,
            wrappedOmitIfEmpty = wrappedKeysConfig?.omitIfEmpty ?: false,
            wrappedOmitIfAbsent = wrappedKeysConfig?.omitIfAbsent ?: emptyList(),
            wrappedUnwrapFields = wrappedUnwrapFields,
            isInferredSignature = prop.hasAnnotation(C.GHOST_SIGNATURE)
        )
    }

    /**
     * Warns if a custom encoder or decoder is configured for the given property.
     */
    private fun warnIfCustomCoder(
        propName: String,
        customDecoder: CustomCoderModel?,
        customEncoder: CustomCoderModel?
    ) {
        if (customDecoder != null || customEncoder != null) {
            logger.info(
                C.STR_WARN_CUSTOM_CODER.format(
                    propName,
                    customDecoder,
                    customEncoder
                )
            )
        }
    }

    /**
     * Determines whether the property or its parent class is annotated as resilient.
     */
    private fun isResilientProperty(prop: KSPropertyDeclaration): Boolean {
        return prop.hasAnnotation(C.GHOST_RESILIENT) || prop.parentDeclaration
            ?.let {
                it is KSClassDeclaration &&
                        it.annotations.any { ann -> ann.shortName.asString() == C.GHOST_RESILIENT }
            } ?: false
    }

    /**
     * Resolves the sealed subclasses of the given type, if it represents a sealed class.
     */
    private fun resolveSealedSubclassesForType(type: KSType): List<KSClassDeclaration> {
        return if (isSealedClass(type)) {
            (type.declaration as KSClassDeclaration).getSealedSubclasses().toList()
        } else {
            emptyList()
        }
    }

    /**
     * Resolves the custom decoder or encoder helper config model if declared.
     */
    private fun resolveCustomCoder(prop: KSPropertyDeclaration, annotationName: String): CustomCoderModel? {
        return prop.annotations.find {
            it.shortName.asString() == annotationName
        }?.let { ann ->
            val provider =
                ann.arguments.find { it.name?.asString() == C.PROVIDER_ARG }?.value as? KSType
            val function =
                ann.arguments.find { it.name?.asString() == C.FUNCTION_NAME_ARG }?.value as? String
            if (provider != null && function != null) {
                CustomCoderModel(
                    provider = provider.toTypeName(),
                    functionName = function,
                    readerKinds = resolveCustomCoderReaderKinds(provider, function),
                )
            } else null
        }
    }

    private fun resolveCustomCoderReaderKinds(
        provider: KSType,
        functionName: String,
    ): Set<CustomCoderReaderKind> {
        val declaration = provider.declaration as? KSClassDeclaration ?: return setOf(CustomCoderReaderKind.BYTES)
        val kinds = declaration.getAllFunctions()
            .filter { it.simpleName.asString() == functionName }
            .mapNotNull { fn ->
                when (fn.parameters.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString()) {
                    C.STR_GHOST_JSON_STRING_READER_QUALIFIED -> CustomCoderReaderKind.STRING
                    C.STR_GHOST_JSON_FLAT_READER_QUALIFIED -> CustomCoderReaderKind.FLAT
                    C.STR_GHOST_JSON_READER_QUALIFIED -> CustomCoderReaderKind.BYTES
                    else -> null
                }
            }
            .toSet()
        return kinds.ifEmpty { setOf(CustomCoderReaderKind.BYTES) }
    }

    private data class WrappedKeysConfig(
        val keys: List<String>,
        val omitIfEmpty: Boolean,
        val omitIfAbsent: List<String>,
    )

    private fun resolveWrappedKeysAnnotation(prop: KSPropertyDeclaration): WrappedKeysConfig? {
        resolveWrappedKeysFromAnnotated(prop)?.let { return it }

        val classDecl = prop.parentDeclaration as? KSClassDeclaration
        val parameter = classDecl?.primaryConstructor?.parameters?.firstOrNull {
            it.name?.asString() == prop.simpleName.asString()
        }
        parameter?.let { resolveWrappedKeysFromAnnotated(it) }?.let { return it }

        return null
    }

    private fun resolveWrappedKeysFromAnnotated(annotated: KSAnnotated): WrappedKeysConfig? {
        try {
            when (annotated) {
                is KSPropertyDeclaration -> {
                    annotated.getAnnotationsByType(GhostWrappedKeys::class)
                        .firstOrNull()
                        ?.let { wrappedKeysConfigFromAnnotation(it, annotated) }
                        ?.let { return it }
                }
                is KSValueParameter -> {
                    annotated.getAnnotationsByType(GhostWrappedKeys::class)
                        .firstOrNull()
                        ?.let { wrappedKeysConfigFromAnnotation(it, annotated) }
                        ?.let { return it }
                }
            }
        } catch (_: Exception) {
            // kspCommonMainKotlinMetadata may throw on getAnnotationsByType for array args.
        }

        return annotated.annotations
            .find { it.shortName.asString() == C.GHOST_WRAPPED_KEYS }
            ?.let { wrappedKeysConfigFromKsAnnotation(it, annotated) }
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
            keys = annotation.readStringArrayArgument(C.KEYS_ARG),
            omitIfEmpty = annotation.readBooleanArgument(C.OMIT_IF_EMPTY_ARG),
            omitIfAbsent = annotation.readStringArrayArgument(C.OMIT_IF_ABSENT_ARG),
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
                is KSValueParameter -> source.name?.asString() ?: C.STR_EMPTY
                else -> C.STR_EMPTY
            }
            logger.error(
                C.STR_ERR_WRAPPED_EMPTY_KEYS_1 + name + C.STR_ERR_WRAPPED_EMPTY_KEYS_2,
                source,
            )
            return WrappedKeysConfig(emptyList(), false, emptyList())
        }
        return WrappedKeysConfig(
            keys = keys,
            omitIfEmpty = omitIfEmpty,
            omitIfAbsent = omitIfAbsent,
        )
    }

    private fun KSAnnotation.readStringArrayArgument(argName: String): List<String> {
        val value = arguments.find { it.name?.asString() == argName }?.value ?: return emptyList()
        return when (value) {
            is Array<*> -> value.filterIsInstance<String>()
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun KSAnnotation.readBooleanArgument(argName: String): Boolean {
        return arguments.find { it.name?.asString() == argName }?.value as? Boolean ?: false
    }

    private fun resolveWrappedUnwrapFields(
        type: KSType,
        wrapperPath: List<String>,
        sourceKeys: List<String>,
    ): List<WrappedUnwrapFieldModel> {
        val declaration = type.declaration as? KSClassDeclaration ?: return emptyList()
        if (!isGhostType(type)) {
            return emptyList()
        }

        val properties = declaration.getAllProperties()
            .filterNot { it.hasAnnotation(C.GHOST_IGNORE) }
            .toList()

        return sourceKeys.mapNotNull { wireKey ->
            resolveUnwrapFieldForWireKey(properties, wrapperPath, wireKey, type)
        }
    }

    private fun resolveUnwrapFieldForWireKey(
        properties: List<KSPropertyDeclaration>,
        wrapperPath: List<String>,
        wireKey: String,
        ownerType: KSType,
    ): WrappedUnwrapFieldModel? {
        val direct = properties.find { getJsonName(it) == wireKey }
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
            val nestedConfig = resolveWrappedKeysAnnotation(property) ?: continue
            if (wireKey !in nestedConfig.keys) {
                continue
            }
            val nestedType = property.type.resolve().makeNotNullable()
            val nestedPath = wrapperPath + property.simpleName.asString()
            val nestedDecl = nestedType.declaration as? KSClassDeclaration ?: continue
            val nestedProps = nestedDecl.getAllProperties()
                .filterNot { it.hasAnnotation(C.GHOST_IGNORE) }
                .toList()
            val leaf = resolveUnwrapFieldForWireKey(nestedProps, nestedPath, wireKey, nestedType)
            if (leaf != null) {
                return leaf
            }
        }

        logger.warn(
            C.STR_WARN_WRAPPED_UNMAPPED_1 + wireKey + C.STR_WARN_WRAPPED_UNMAPPED_2 +
                ownerType.declaration.simpleName.asString(),
        )
        return null
    }

    /**
     * Resolves the flatten/wrap key paths from annotations.
     */
    private fun resolvePathAnnotation(prop: KSPropertyDeclaration, annotationName: String): List<String>? {
        return prop.annotations.find {
            it.shortName.asString() == annotationName
        }?.let { ann ->
            val path = ann.arguments.find { it.name?.asString() == C.PATH_ARG }?.value as? String
            path?.split(C.STR_DOT)
        }
    }

    /**
     * Validates that map keys are Strings.
     */
    private fun validateMapKey(
        prop: KSPropertyDeclaration,
        isMap: Boolean,
        mapKeyType: KSType?
    ) {
        if (isMap && mapKeyType?.declaration?.qualifiedName?.asString() != C.STRING_QUALIFIED) {
            logger.error(
                "${C.STR_ERR_MAP_1}${prop.simpleName.asString()}${C.STR_ERR_MAP_2}" +
                        C.STR_ERR_MAP_3,
                prop
            )
        }
    }

    /**
     * Checks if a type requires contextual serialization (e.g., non-built-in/third-party types).
     */
    private fun isContextualType(
        type: KSType,
        isList: Boolean,
        isSet: Boolean,
        isMap: Boolean,
        isPrimitiveArray: Boolean
    ): Boolean {
        if (isList || isSet || isMap || isPrimitiveArray) {
            return false
        }
        if (isGhostType(type)) {
            return false
        }
        if (isEnumType(type)) {
            return false
        }

        val qualifiedName = type.declaration.qualifiedName?.asString()
        val isBuiltIn = qualifiedName?.startsWith(C.STR_KOTLIN_PREFIX) == true ||
                qualifiedName?.startsWith(C.STR_JAVA_PREFIX) == true

        if (isBuiltIn) {
            return when (qualifiedName) {
                C.K_STRING, C.K_INT, C.K_LONG, C.K_DOUBLE, C.K_FLOAT,
                C.K_BOOLEAN, C.K_BYTE, C.K_SHORT, C.K_CHAR, C.K_UNIT, C.K_ANY,
                C.K_BYTE_ARRAY -> false

                else -> true
            }
        }

        if (qualifiedName == C.K_RAW_JSON) {
            return false
        }

        return true // Third party types
    }

    /**
     * Checks if the type is a value class or inline class.
     */
    private fun isValueClass(type: KSType): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        return declaration.modifiers.contains(Modifier.VALUE) ||
                declaration.modifiers.contains(Modifier.INLINE)
    }

    /**
     * Checks if the type is a sealed class.
     */
    private fun isSealedClass(type: KSType): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        return declaration.modifiers.contains(Modifier.SEALED)
    }

    /**
     * Resolves the underlying property model for a value class type.
     */
    private fun resolveValueClassProperty(type: KSType): GhostPropertyModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        val primaryConstructor = declaration.primaryConstructor ?: return null
        val param = primaryConstructor.parameters.firstOrNull() ?: return null
        val prop = declaration.getAllProperties()
            .find { it.simpleName.asString() == param.name?.asString() } ?: return null
        return buildPropertyModel(prop, listOf(param))
    }

    /**
     * Resolves the first type argument of a generic type.
     */
    private fun resolveFirstTypeArg(type: KSType): KSType? {
        return type.arguments.firstOrNull()?.type?.resolve()
    }

    /**
     * Resolves the second type argument of a generic type.
     */
    private fun resolveSecondTypeArg(type: KSType): KSType? {
        return type.arguments.getOrNull(1)?.type?.resolve()
    }

    /**
     * Returns the serialized JSON name of a property.
     */
    private fun getJsonName(prop: KSPropertyDeclaration): String = getSerialName(prop)

    /**
     * Extension to check if a property has a specific annotation by name.
     */
    private fun KSPropertyDeclaration.hasAnnotation(name: String): Boolean {
        return annotations.any { it.shortName.asString() == name }
    }

    /**
     * Resolves the serialized name for an annotated element, checking GhostName or kotlinx SerialName.
     */
    internal fun getSerialName(declaration: KSAnnotated): String {
        val annotations = declaration.annotations.toList()

        // 1. GhostName (Primary)
        val ghostName = annotations.find { it.shortName.asString() == C.GHOST_NAME }
        if (ghostName != null) {
            val arg = ghostName.arguments.find { it.name?.asString() == C.NAME_ARG }
                ?: ghostName.arguments.firstOrNull()
            return arg?.value?.toString() ?: C.STR_EMPTY
        }

        // 2. SerialName (kotlinx compatibility)
        val serialName = annotations.find {
            val name = it.shortName.asString()
            name == C.SERIAL_NAME || name.endsWith(C.STR_SERIAL_NAME_SUFFIX)
        }

        if (serialName != null) {
            val arg = serialName.arguments.find { it.name?.asString() == C.STR_VALUE_ARG }
                ?: serialName.arguments.firstOrNull()

            return arg?.value?.toString()
                ?: (declaration as? KSDeclaration)?.simpleName?.asString()
                ?: C.STR_EMPTY
        }

        return (declaration as? KSDeclaration)
            ?.simpleName?.asString()
            ?: C.STR_EMPTY
    }

    /**
     * Checks if the type is an enum class.
     */
    private fun isEnumType(type: KSType): Boolean =
        (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

    /**
     * Checks if the type is annotated with @GhostSerialization.
     */
    private fun isGhostType(type: KSType): Boolean =
        type.declaration.annotations.any { it.shortName.asString() == C.GHOST_SERIALIZATION }

    companion object {
        /**
         * Set of fully qualified primitive array types.
         */
        private val PRIMITIVE_ARRAYS = setOf(
            C.STR_TYPE_INT_ARRAY,
            C.STR_TYPE_LONG_ARRAY,
            C.STR_TYPE_FLOAT_ARRAY,
            C.STR_TYPE_DOUBLE_ARRAY,
            C.STR_TYPE_BOOLEAN_ARRAY
        )
    }
}