package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.ghost.serialization.compiler.model.InferredSubclassModel
import com.ghost.serialization.compiler.model.WrappedKeysModel
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
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
 * Validates a class declaration against Ghost's serialization rules (class kind, property
 * visibility, map key type, unique JSON names) and converts its properties into [GhostPropertyModel]s.
 */
internal class GhostAnalyzer(private val logger: KSPLogger) {

    private val wrappedKeysAnalyzer = WrappedKeysAnalyzer(logger = logger)
    private val customCoderAnalyzer = CustomCoderAnalyzer(logger = logger)

    fun analyze(classDeclaration: KSClassDeclaration): List<GhostPropertyModel> {
        val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
        val isData = classDeclaration.modifiers.contains(Modifier.DATA)
        val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
                classDeclaration.modifiers.contains(Modifier.INLINE)
        val isObject = classDeclaration.classKind == ClassKind.OBJECT

        val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS

        if (isObject) return emptyList()

        validateClassKind(
            classDeclaration = classDeclaration,
            isData = isData,
            isSealed = isSealed,
            isValue = isValue,
            isEnum = isEnum
        )

        val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()

        val allProps = classDeclaration.getAllProperties().toList()
        val overriddenProps = allProps.mapNotNull { it.findOverridee() }.toSet()
        val properties = allProps
            .filterNot { it in overriddenProps }
            .filterNot { it.hasAnnotation(name = AC.GHOST_IGNORE) }
            .toList()

        validatePropertyVisibility(classDeclaration = classDeclaration, properties = properties)

        val enumValues = getEnumValues(classDeclaration = classDeclaration, isEnum = isEnum)
        val propertyModels =
            resolvePropertyModels(
                classDeclaration = classDeclaration,
                properties = properties,
                parameters = parameters,
                isEnum = isEnum,
                enumValues = enumValues
            )

        val finalModels = resolveSealedSubclasses(
            classDeclaration = classDeclaration,
            propertyModels = propertyModels,
            isSealed = isSealed
        )

        validateNames(properties = finalModels, clazz = classDeclaration)
        wrappedKeysAnalyzer.validateWrappedKeys(properties = finalModels, clazz = classDeclaration)
        return finalModels
    }

    private fun buildPropertyModel(
        prop: KSPropertyDeclaration,
        parameters: List<KSValueParameter>
    ): GhostPropertyModel {
        val type = prop.type.resolve()
        val qualifiedName = type.declaration.qualifiedName?.asString()

        val isList = qualifiedName == AC.LIST_QUALIFIED
        val isSet = qualifiedName == AC.SET_QUALIFIED
        val isMap = qualifiedName == AC.MAP_QUALIFIED

        val innerType = if (isList || isSet) {
            resolveFirstTypeArg(type = type)
        } else {
            null
        }
        val mapKeyType = if (isMap) {
            resolveFirstTypeArg(type = type)
        } else {
            null
        }
        val mapValueType = if (isMap) {
            resolveSecondTypeArg(type = type)
        } else {
            null
        }

        validateMapKey(prop = prop, isMap = isMap, mapKeyType = mapKeyType)

        val param = parameters.find {
            it.name?.asString() == prop.simpleName.asString()
        }

        val isPrimitiveArray = qualifiedName in PRIMITIVE_ARRAYS
        val primitiveArrayType =
            if (isPrimitiveArray) {
                qualifiedName?.removePrefix(AC.STR_KOTLIN_DOT)
            } else {
                null
            }

        val customDecoder = customCoderAnalyzer.resolveCustomCoder(prop = prop, annotationName = AC.GHOST_DECODER)
        val customEncoder = customCoderAnalyzer.resolveCustomCoder(prop = prop, annotationName = AC.GHOST_ENCODER)

        val flattenPath = resolvePathAnnotation(prop = prop, annotationName = AC.GHOST_FLATTEN)
        val wrapPath = resolvePathAnnotation(prop = prop, annotationName = AC.GHOST_WRAP)
        val wrappedKeysConfig = wrappedKeysAnalyzer.resolveWrappedKeysAnnotation(prop = prop)

        customCoderAnalyzer.warnIfCustomCoder(
            propName = prop.simpleName.asString(),
            customDecoder = customDecoder,
            customEncoder = customEncoder
        )

        val parentClass = prop.parentDeclaration as? KSClassDeclaration
        val hasProto = parentClass?.annotations?.any {
            it.shortName.asString() == CC.ANNOTATION_GHOST_PROTO_SERIALIZATION
        } == true

        val serialNameAnnotation = prop.annotations.any {
            val name = it.shortName.asString()
            name == AC.GHOST_NAME || name == AC.SERIAL_NAME || name.endsWith(AC.STR_SERIAL_NAME_SUFFIX)
        }

        var jsonName = flattenPath?.last() ?: getJsonName(prop = prop)
        if (hasProto && !serialNameAnnotation) {
            jsonName = toLowerCamelCase(str = jsonName)
        }

        val wrappedUnwrapFields = if (wrappedKeysConfig != null) {
            wrappedKeysAnalyzer.resolveWrappedUnwrapFields(
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
            isGhost = isGhostType(type = type),
            isList = isList,
            isSet = isSet,
            listInnerType = innerType,
            isEnum = isEnumType(type = type),
            listInnerIsGhost = innerType?.let { isGhostType(type = it) } ?: false,
            listInnerIsEnum = innerType?.let { isEnumType(type = it) } ?: false,
            hasDefaultValue = param?.hasDefault ?: false,
            defaultExpression = param
                ?.takeIf { it.hasDefault }
                ?.let { DefaultExpressionExtractor.extract(param = it) }
                ?.let { expression ->
                    DefaultExpressionQualifier.qualify(
                        expression = expression,
                        model = prop.parentDeclaration as? KSClassDeclaration
                    )
                },
            isInConstructor = param != null,
            isMap = isMap,
            mapValueType = mapValueType,
            mapValueIsGhost = mapValueType?.let { isGhostType(type = it) } ?: false,
            isPrimitiveArray = isPrimitiveArray,
            primitiveArrayType = primitiveArrayType,
            isValueClass = isValueClass(type = type) && !type.isKotlinUnsignedPrimitive(),
            valueClassProperty = if (isValueClass(type = type) && !type.isKotlinUnsignedPrimitive()) {
                resolveValueClassProperty(type = type, isProto = hasProto)
            } else {
                null
            },
            isSealedClass = isSealedClass(type = type),
            sealedSubclasses = resolveSealedSubclassesForType(type = type),
            isResilient = isResilientProperty(prop = prop),
            isContextual = isContextualType(
                type = type,
                isList = isList,
                isSet = isSet,
                isMap = isMap,
                isPrimitiveArray = isPrimitiveArray
            ),
            customDecoder = customDecoder,
            customEncoder = customEncoder,
            flattenPath = flattenPath,
            wrapPath = wrapPath,
            wrappedKeys = wrappedKeysConfig?.let {
                WrappedKeysModel(
                    sourceKeys = it.keys,
                    omitIfEmpty = it.omitIfEmpty,
                    omitIfAbsent = it.omitIfAbsent,
                    unwrapFields = wrappedUnwrapFields,
                )
            },
            isInferredSignature = prop.hasAnnotation(name = AC.GHOST_SIGNATURE),
            isProto = hasProto
        )
    }

    private fun getEnumValues(
        classDeclaration: KSClassDeclaration,
        isEnum: Boolean
    ): Map<String, String>? {
        return if (isEnum) {
            classDeclaration.declarations
                .filter { it is KSClassDeclaration && it.classKind == ClassKind.ENUM_ENTRY }
                .map { it as KSClassDeclaration }
                .associate { entry -> entry.simpleName.asString() to getSerialName(declaration = entry) }
        } else null
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
        val isCollectionOrPrimitiveArray = isList || isSet || isMap || isPrimitiveArray
        if (isCollectionOrPrimitiveArray) {
            return false
        }
        if (isGhostType(type = type)) {
            return false
        }
        if (isEnumType(type = type)) {
            return false
        }

        val qualifiedName = type.declaration.qualifiedName?.asString()
        val isBuiltIn = qualifiedName?.startsWith(AC.STR_KOTLIN_PREFIX) == true ||
                qualifiedName?.startsWith(AC.STR_JAVA_PREFIX) == true

        if (isBuiltIn) {
            return when (qualifiedName) {
                AC.K_STRING, AC.K_INT, AC.K_LONG, AC.K_ULONG, AC.K_UINT, AC.K_USHORT, AC.K_UBYTE,
                AC.K_DOUBLE, AC.K_FLOAT,
                AC.K_BOOLEAN, AC.K_BYTE, AC.K_SHORT, AC.K_CHAR, AC.K_UNIT, AC.K_ANY,
                AC.K_BYTE_ARRAY -> false

                else -> true
            }
        }

        if (qualifiedName == AC.K_RAW_JSON) {
            return false
        }

        return true // Third party types
    }

    /** True if the property or its declaring class is annotated `@GhostResilient`. */
    private fun isResilientProperty(prop: KSPropertyDeclaration): Boolean {
        return prop.hasAnnotation(name = AC.GHOST_RESILIENT) || prop.parentDeclaration
            ?.let {
                it is KSClassDeclaration &&
                        it.annotations.any { ann -> ann.shortName.asString() == AC.GHOST_RESILIENT }
            } ?: false
    }

    private fun resolvePathAnnotation(
        prop: KSPropertyDeclaration,
        annotationName: String
    ): List<String>? {
        return prop.annotations.find {
            it.shortName.asString() == annotationName
        }?.let { ann ->
            val path = ann.arguments.find { it.name?.asString() == AC.PATH_ARG }?.value as? String
            path?.split(CC.STR_DOT)
        }
    }

    /** For enums, returns a single synthetic `name` property model; otherwise maps declared properties. */
    private fun resolvePropertyModels(
        classDeclaration: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>,
        parameters: List<KSValueParameter>,
        isEnum: Boolean,
        enumValues: Map<String, String>?
    ): List<GhostPropertyModel> {
        return if (isEnum) {
            listOf(
                GhostPropertyModel(
                    kotlinName = AC.NAME,
                    jsonName = AC.NAME,
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
            properties.map { prop -> buildPropertyModel(prop = prop, parameters = parameters) }
        }
    }

    /**
     * Attaches inferred subclass metadata for sealed classes; falls back to a placeholder
     * property model when the class has none of its own.
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
                        declaration = subclass,
                        properties = analyze(subclass)
                    )
                }
                .toList()
            propertyModels.map { it.copy(inferredSubclasses = inferredSubclasses) }
                .ifEmpty {
                    listOf(
                        GhostPropertyModel(
                            kotlinName = CC.STR_EMPTY,
                            jsonName = CC.STR_EMPTY,
                            type = classDeclaration.asType(emptyList()),
                            typeName = classDeclaration.toClassName(),
                            isNullable = false,
                            isGhost = false,
                            isList = false,
                            isSet = false,
                            isEnum = false,
                            inferredSubclasses = inferredSubclasses
                        )
                    )
                }
        } else {
            propertyModels
        }
    }

    private fun resolveSealedSubclassesForType(type: KSType): List<KSClassDeclaration> {
        return if (isSealedClass(type = type)) {
            (type.declaration as KSClassDeclaration).getSealedSubclasses().toList()
        } else {
            emptyList()
        }
    }

    private fun resolveValueClassProperty(
        type: KSType,
        isProto: Boolean = false
    ): GhostPropertyModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        val primaryConstructor = declaration.primaryConstructor ?: return null
        val param = primaryConstructor.parameters.firstOrNull() ?: return null
        val prop = declaration.getAllProperties()
            .find { it.simpleName.asString() == param.name?.asString() } ?: return null
        // The value class itself (e.g. `UserId`) is never @GhostProtoSerialization-annotated;
        // it inherits proto-ness from whichever outer property wraps it.
        return buildPropertyModel(prop = prop, parameters = listOf(param)).copy(isProto = isProto)
    }

    private fun validateClassKind(
        classDeclaration: KSClassDeclaration,
        isData: Boolean,
        isSealed: Boolean,
        isValue: Boolean,
        isEnum: Boolean
    ) {
        val isRegularClass = !isData && !isSealed && !isValue && !isEnum
        if (isRegularClass) {
            logger.error(
                AC.STR_ERR_CLASS_1 +
                        "${AC.STR_ERR_CLASS_2}${
                            classDeclaration
                                .simpleName
                                .asString()
                        }${AC.STR_ERR_CLASS_3}",
                classDeclaration
            )
        }
    }

    private fun validateMapKey(
        prop: KSPropertyDeclaration,
        isMap: Boolean,
        mapKeyType: KSType?
    ) {
        if (isMap && mapKeyType?.declaration?.qualifiedName?.asString() != AC.STRING_QUALIFIED) {
            logger.error(
                "${AC.STR_ERR_MAP_1}${prop.simpleName.asString()}${AC.STR_ERR_MAP_2}" +
                        AC.STR_ERR_MAP_3,
                prop
            )
        }
    }

    private fun validateNames(
        properties: List<GhostPropertyModel>,
        clazz: KSClassDeclaration
    ) {
        val names = properties.groupBy { it.jsonName }
        names.forEach { (name, props) ->
            if (props.size > CC.VAL_ONE) {
                logger.error(
                    "${AC.STR_ERR_DUP_1}$name${AC.STR_ERR_DUP_2}${
                        clazz
                            .simpleName
                            .asString()
                    }${AC.STR_ERR_DUP_3}" +
                            "${AC.STR_ERR_DUP_4}${props.joinToString { it.kotlinName }}",
                    clazz
                )
            }
        }
    }

    /** Rejects properties declared private. */
    private fun validatePropertyVisibility(
        classDeclaration: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>
    ) {
        val hasPrivateProperties = properties.any {
            it.modifiers.contains(Modifier.PRIVATE)
        }
        if (hasPrivateProperties) {
            logger.error(
                AC.STR_ERR_PRIV_1 +
                        "${AC.STR_ERR_PRIV_2}${
                            classDeclaration
                                .simpleName
                                .asString()
                        }${AC.STR_ERR_PRIV_3}",
                classDeclaration
            )
        }
    }

    companion object {
        private val PRIMITIVE_ARRAYS = setOf(
            AC.STR_TYPE_INT_ARRAY,
            AC.STR_TYPE_LONG_ARRAY,
            AC.STR_TYPE_FLOAT_ARRAY,
            AC.STR_TYPE_DOUBLE_ARRAY,
            AC.STR_TYPE_BOOLEAN_ARRAY
        )
    }
}
