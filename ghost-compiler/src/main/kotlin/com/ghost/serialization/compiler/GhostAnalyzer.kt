package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

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

    fun analyze(classDeclaration: KSClassDeclaration): List<GhostPropertyModel> {
        val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
        val isData = classDeclaration.modifiers.contains(Modifier.DATA)
        val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
                classDeclaration.modifiers.contains(Modifier.INLINE)

        val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS

        if (!isData && !isSealed && !isValue && !isEnum) {
            logger.error(
                C.STR_ERR_CLASS_1 +
                        "${C.STR_ERR_CLASS_2}${classDeclaration.simpleName.asString()}${C.STR_ERR_CLASS_3}",
                classDeclaration
            )
        }

        val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()

        val properties = classDeclaration.getAllProperties()
            .filterNot { it.hasAnnotation(C.GHOST_IGNORE) }
            .toList()

        val hasPrivateProperties = properties.any {
            it.modifiers.contains(Modifier.PRIVATE)
        }
        if (hasPrivateProperties) {
            logger.error(
                C.STR_ERR_PRIV_1 +
                        "${C.STR_ERR_PRIV_2}${classDeclaration.simpleName.asString()}${C.STR_ERR_PRIV_3}",
                classDeclaration
            )
        }

        val enumValues = getEnumValues(classDeclaration, isEnum)

        val propertyModels = if (isEnum) {
            properties
                .filterNot { it.simpleName.asString() in listOf(C.NAME, C.ORDINAL) }
                .map { prop -> buildPropertyModel(prop, parameters).copy(enumValues = enumValues) }
                .let {
                    it.ifEmpty {
                        listOf(
                            GhostPropertyModel(
                                kotlinName = C.NAME,
                                jsonName = C.NAME,
                                type = classDeclaration.asType(emptyList()),
                                typeName = classDeclaration.toClassName(),
                                isNullable = false,
                                isGhost = false,
                                isList = false,
                                isEnum = true,
                                enumValues = enumValues
                            )
                        )
                    }
                }
        } else {
            properties.map { prop -> buildPropertyModel(prop, parameters) }
        }
        validateNames(propertyModels, classDeclaration)
        return propertyModels
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

    private fun validateNames(properties: List<GhostPropertyModel>, clazz: KSClassDeclaration) {
        val names = properties.groupBy { it.jsonName }
        names.forEach { (name, props) ->
            if (props.size > 1) {
                logger.error(
                    "${C.STR_ERR_DUP_1}$name${C.STR_ERR_DUP_2}${clazz.simpleName.asString()}${C.STR_ERR_DUP_3}" +
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
        val isMap = qualifiedName == C.MAP_QUALIFIED

        val innerType = if (isList) resolveFirstTypeArg(type) else null
        val mapKeyType = if (isMap) resolveFirstTypeArg(type) else null
        val mapValueType = if (isMap) resolveSecondTypeArg(type) else null

        if (isMap && mapKeyType?.declaration?.qualifiedName?.asString() != C.STRING_QUALIFIED) {
            logger.error(
                "${C.STR_ERR_MAP_1}${prop.simpleName.asString()}${C.STR_ERR_MAP_2}" +
                        C.STR_ERR_MAP_3,
                prop
            )
        }

        val param = parameters.find {
            it.name?.asString() == prop.simpleName.asString()
        }

        val isPrimitiveArray = qualifiedName in PRIMITIVE_ARRAYS
        val primitiveArrayType =
            if (isPrimitiveArray) qualifiedName?.removePrefix(C.STR_KOTLIN_DOT) else null

        val customDecoder = prop.annotations.find {
            it.shortName.asString() == C.GHOST_DECODER
        }?.let { ann ->
            val provider =
                ann.arguments.find { it.name?.asString() == C.PROVIDER_ARG }?.value as? KSType
            val function =
                ann.arguments.find { it.name?.asString() == C.FUNCTION_NAME_ARG }?.value as? String
            if (provider != null && function != null) {
                CustomCoderModel(provider.toTypeName(), function)
            } else null
        }

        val customEncoder = prop.annotations.find {
            it.shortName.asString() == C.GHOST_ENCODER
        }?.let { ann ->
            val provider =
                ann.arguments.find { it.name?.asString() == C.PROVIDER_ARG }?.value as? KSType
            val function =
                ann.arguments.find { it.name?.asString() == C.FUNCTION_NAME_ARG }?.value as? String
            if (provider != null && function != null) {
                CustomCoderModel(provider.toTypeName(), function)
            } else null
        }

        val flattenPath = prop.annotations.find {
            it.shortName.asString() == C.GHOST_FLATTEN
        }?.let { ann ->
            val path = ann.arguments.find { it.name?.asString() == C.PATH_ARG }?.value as? String
            path?.split(C.STR_DOT)
        }

        val wrapPath = prop.annotations.find {
            it.shortName.asString() == C.GHOST_WRAP
        }?.let { ann ->
            val path = ann.arguments.find { it.name?.asString() == C.PATH_ARG }?.value as? String
            path?.split(C.STR_DOT)
        }

        if (customDecoder != null || customEncoder != null) {
            logger.warn("Detected custom coder for ${prop.simpleName.asString()}: D=$customDecoder, E=$customEncoder")
        }

        val jsonName = if (flattenPath != null) {
            flattenPath.last()
        } else {
            getJsonName(prop)
        }

        return GhostPropertyModel(
            kotlinName = prop.simpleName.asString(),
            jsonName = jsonName,
            type = type,
            typeName = type.toTypeName(),
            isNullable = type.isMarkedNullable,
            isGhost = isGhostType(type),
            isList = isList,
            listInnerType = innerType,
            isEnum = isEnumType(type),
            listInnerIsGhost = innerType?.let { isGhostType(it) } ?: false,
            listInnerIsEnum = innerType?.let { isEnumType(it) } ?: false,
            hasDefaultValue = param?.hasDefault ?: false,
            isMap = isMap,
            mapValueType = mapValueType,
            mapValueIsGhost = mapValueType?.let { isGhostType(it) } ?: false,
            isPrimitiveArray = isPrimitiveArray,
            primitiveArrayType = primitiveArrayType,
            isValueClass = isValueClass(type),
            valueClassProperty = if (isValueClass(type)) resolveValueClassProperty(type) else null,
            isSealedClass = isSealedClass(type),
            sealedSubclasses = if (isSealedClass(type)) {
                (type.declaration as KSClassDeclaration).getSealedSubclasses().toList()
            } else {
                emptyList()
            },
            isResilient = prop.hasAnnotation(C.GHOST_RESILIENT) || prop.parentDeclaration
                ?.let {
                    it is KSClassDeclaration &&
                            it.annotations.any { ann -> ann.shortName.asString() == C.GHOST_RESILIENT }
                } ?: false,
            isContextual = isContextualType(type, isList, isMap, isPrimitiveArray),
            customDecoder = customDecoder,
            customEncoder = customEncoder,
            flattenPath = flattenPath,
            wrapPath = wrapPath
        )
    }

    private fun isContextualType(
        type: KSType,
        isList: Boolean,
        isMap: Boolean,
        isPrimitiveArray: Boolean
    ): Boolean {
        if (isList || isMap || isPrimitiveArray) return false
        if (isGhostType(type)) return false
        if (isEnumType(type)) return false

        val qualifiedName = type.declaration.qualifiedName?.asString()
        val isBuiltIn = qualifiedName?.startsWith(C.STR_KOTLIN_PREFIX) == true ||
                qualifiedName?.startsWith(C.STR_JAVA_PREFIX) == true

        if (isBuiltIn) {
            return when (qualifiedName) {
                C.K_STRING, C.K_INT, C.K_LONG, C.K_DOUBLE, C.K_FLOAT,
                C.K_BOOLEAN, C.K_BYTE, C.K_SHORT, C.K_CHAR, C.K_UNIT, C.K_ANY -> false

                else -> true
            }
        }

        return true // Third party types
    }

    private fun isValueClass(type: KSType): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        return declaration.modifiers.contains(Modifier.VALUE) ||
                declaration.modifiers.contains(Modifier.INLINE)
    }

    private fun isSealedClass(type: KSType): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        return declaration.modifiers.contains(Modifier.SEALED)
    }

    private fun resolveValueClassProperty(type: KSType): GhostPropertyModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        val primaryConstructor = declaration.primaryConstructor ?: return null
        val param = primaryConstructor.parameters.firstOrNull() ?: return null
        val prop = declaration.getAllProperties()
            .find { it.simpleName.asString() == param.name?.asString() } ?: return null
        return buildPropertyModel(prop, listOf(param))
    }

    private fun resolveFirstTypeArg(type: KSType): KSType? {
        return type.arguments.firstOrNull()?.type?.resolve()
    }

    private fun resolveSecondTypeArg(type: KSType): KSType? {
        return type.arguments.getOrNull(1)?.type?.resolve()
    }

    private fun getJsonName(prop: KSPropertyDeclaration): String = getSerialName(prop)

    private fun KSPropertyDeclaration.hasAnnotation(name: String): Boolean {
        return annotations.any { it.shortName.asString() == name }
    }

    internal fun getSerialName(declaration: com.google.devtools.ksp.symbol.KSAnnotated): String {
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
                ?: (declaration as? com.google.devtools.ksp.symbol.KSDeclaration)?.simpleName?.asString()
                ?: C.STR_EMPTY
        }

        return (declaration as? com.google.devtools.ksp.symbol.KSDeclaration)
            ?.simpleName?.asString()
            ?: C.STR_EMPTY
    }

    private fun isEnumType(type: KSType): Boolean =
        (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

    private fun isGhostType(type: KSType): Boolean =
        type.declaration.annotations.any { it.shortName.asString() == C.GHOST_SERIALIZATION }

    companion object {
        private val PRIMITIVE_ARRAYS = setOf(
            C.STR_TYPE_INT_ARRAY,
            C.STR_TYPE_LONG_ARRAY,
            C.STR_TYPE_FLOAT_ARRAY,
            C.STR_TYPE_DOUBLE_ARRAY,
            C.STR_TYPE_BOOLEAN_ARRAY
        )
    }
}