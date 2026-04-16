package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ksp.toTypeName

internal class GhostAnalyzer(private val logger: KSPLogger) {

    fun analyze(classDeclaration: KSClassDeclaration): List<GhostPropertyModel> {
        val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
        val isData = classDeclaration.modifiers.contains(Modifier.DATA)
        val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
                classDeclaration.modifiers.contains(Modifier.INLINE)
        val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS

        if (!isData && !isSealed && !isValue && !isEnum) {
            logger.error(
                STR_ERR_CLASS_1 +
                        "$STR_ERR_CLASS_2${classDeclaration.simpleName.asString()}$STR_ERR_CLASS_3",
                classDeclaration
            )
        }

        val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()

        val properties = classDeclaration.getAllProperties()
            .filterNot { it.hasAnnotation(GHOST_IGNORE) }
            .toList()

        val hasPrivateProperties = properties.any {
            it.modifiers.contains(Modifier.PRIVATE)
        }
        if (hasPrivateProperties) {
            logger.error(
                STR_ERR_PRIV_1 +
                        "$STR_ERR_PRIV_2${classDeclaration.simpleName.asString()}$STR_ERR_PRIV_3",
                classDeclaration
            )
        }

        val propertyModels = properties.map { prop -> buildPropertyModel(prop, parameters) }
        validateNames(propertyModels, classDeclaration)
        return propertyModels
    }

    private fun validateNames(properties: List<GhostPropertyModel>, clazz: KSClassDeclaration) {
        val names = properties.groupBy { it.jsonName }
        names.forEach { (name, props) ->
            if (props.size > 1) {
                logger.error(
                    "$STR_ERR_DUP_1$name$STR_ERR_DUP_2${clazz.simpleName.asString()}$STR_ERR_DUP_3" +
                            "$STR_ERR_DUP_4${props.joinToString { it.kotlinName }}",
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

        val isList = qualifiedName == LIST_QUALIFIED
        val isMap = qualifiedName == MAP_QUALIFIED

        val innerType = if (isList) resolveFirstTypeArg(type) else null
        val mapKeyType = if (isMap) resolveFirstTypeArg(type) else null
        val mapValueType = if (isMap) resolveSecondTypeArg(type) else null

        if (isMap && mapKeyType?.declaration?.qualifiedName?.asString() != STRING_QUALIFIED) {
            logger.error(
                "$STR_ERR_MAP_1${prop.simpleName.asString()}$STR_ERR_MAP_2" +
                        STR_ERR_MAP_3,
                prop
            )
        }

        val param = parameters.find {
            it.name?.asString() == prop.simpleName.asString()
        }

        val isPrimitiveArray = qualifiedName in PRIMITIVE_ARRAYS
        val primitiveArrayType =
            if (isPrimitiveArray) qualifiedName?.removePrefix(STR_KOTLIN_DOT) else null

        return GhostPropertyModel(
            kotlinName = prop.simpleName.asString(),
            jsonName = getJsonName(prop),
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
            sealedSubclasses = if (isSealedClass(type)) (type.declaration as KSClassDeclaration).getSealedSubclasses()
                .toList() else emptyList()
        )
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

    private fun getJsonName(prop: KSPropertyDeclaration): String {
        val annotation = prop.annotations.find {
            it.shortName.asString() == GHOST_NAME
        }
        return annotation?.arguments
            ?.firstOrNull { it.name?.asString() == NAME_ARG }
            ?.value as? String
            ?: prop.simpleName.asString()
    }

    private fun KSPropertyDeclaration.hasAnnotation(name: String): Boolean {
        return annotations.any { it.shortName.asString() == name }
    }

    private fun isEnumType(type: KSType): Boolean {
        return (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
    }

    private fun isGhostType(type: KSType): Boolean {
        return type.declaration.annotations.any {
            it.shortName.asString() == GHOST_SERIALIZATION
        }
    }

    companion object {
        private const val STR_ERR_CLASS_1 = "GhostSerialization: @GhostSerialization can only be applied to 'data class', 'sealed class', 'value class' or 'enum class'. "
        private const val STR_ERR_CLASS_2 = "Class '"
        private const val STR_ERR_CLASS_3 = "' is not supported."
        private const val STR_ERR_PRIV_1 = "GhostSerialization: Properties in @GhostSerialization classes cannot be private. "
        private const val STR_ERR_PRIV_2 = "Please remove 'private' modifier from properties in '"
        private const val STR_ERR_PRIV_3 = "'."
        private const val STR_ERR_DUP_1 = "GhostSerialization: Duplicate JSON name '"
        private const val STR_ERR_DUP_2 = "' found in class '"
        private const val STR_ERR_DUP_3 = "'. "
        private const val STR_ERR_DUP_4 = "Problematic properties: "
        private const val STR_ERR_MAP_1 = "GhostSerialization: Map key must be a String in property '"
        private const val STR_ERR_MAP_2 = "'. "
        private const val STR_ERR_MAP_3 = "JSON only supports string-keyed objects."
        private const val STR_KOTLIN_DOT = "kotlin."
        private const val STR_TYPE_INT_ARRAY = "kotlin.IntArray"
        private const val STR_TYPE_LONG_ARRAY = "kotlin.LongArray"
        private const val STR_TYPE_FLOAT_ARRAY = "kotlin.FloatArray"
        private const val STR_TYPE_DOUBLE_ARRAY = "kotlin.DoubleArray"
        private const val STR_TYPE_BOOLEAN_ARRAY = "kotlin.BooleanArray"
        private const val GHOST_IGNORE = "GhostIgnore"
        private const val GHOST_NAME = "GhostName"
        private const val GHOST_SERIALIZATION = "GhostSerialization"
        private const val NAME_ARG = "name"
        private const val LIST_QUALIFIED = "kotlin.collections.List"
        private const val MAP_QUALIFIED = "kotlin.collections.Map"
        private const val STRING_QUALIFIED = "kotlin.String"
        private val PRIMITIVE_ARRAYS = setOf(
            STR_TYPE_INT_ARRAY,
            STR_TYPE_LONG_ARRAY,
            STR_TYPE_FLOAT_ARRAY,
            STR_TYPE_DOUBLE_ARRAY,
            STR_TYPE_BOOLEAN_ARRAY
        )
    }
}