package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName

internal class GhostAnalyzer(private val logger: KSPLogger) {

    fun analyze(classDeclaration: KSClassDeclaration): List<GhostPropertyModel> {
        val isSealed = classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.SEALED)
        val isData = classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.DATA)
        val isValue = classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.VALUE) || 
                       classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.INLINE)

        if (!isData && !isSealed && !isValue) {
            logger.error(
                "GhostSerialization: @GhostSerialization can only be applied to 'data class', 'sealed class' or 'value class'. " +
                    "Class '${classDeclaration.simpleName.asString()}' is not supported.",
                classDeclaration
            )
        }

        val parameters = classDeclaration.primaryConstructor?.parameters ?: emptyList()

        val properties = classDeclaration.getAllProperties()
            .filterNot { it.hasAnnotation(GHOST_IGNORE) }
            .toList()

        val hasPrivateProperties = properties.any {
            it.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.PRIVATE)
        }
        if (hasPrivateProperties) {
            logger.error(
                "GhostSerialization: Properties in @GhostSerialization classes cannot be private. " +
                    "Please remove 'private' modifier from properties in '${classDeclaration.simpleName.asString()}'.",
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
                    "GhostSerialization: Duplicate JSON name '$name' found in class '${clazz.simpleName.asString()}'. " +
                        "Problematic properties: ${props.joinToString { it.kotlinName }}",
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
                "GhostSerialization: Map key must be a String in property '${prop.simpleName.asString()}'. " +
                    "JSON only supports string-keyed objects.",
                prop
            )
        }

        val param = parameters.find {
            it.name?.asString() == prop.simpleName.asString()
        }

        val isPrimitiveArray = qualifiedName in PRIMITIVE_ARRAYS
        val primitiveArrayType = if (isPrimitiveArray) qualifiedName?.removePrefix("kotlin.") else null

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
            sealedSubclasses = if (isSealedClass(type)) (type.declaration as KSClassDeclaration).getSealedSubclasses().toList() else emptyList()
        )
    }

    private fun isValueClass(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        return decl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.VALUE) || 
               decl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.INLINE)
    }

    private fun isSealedClass(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        return decl.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.SEALED)
    }

    private fun resolveValueClassProperty(type: KSType): GhostPropertyModel? {
        val decl = type.declaration as? KSClassDeclaration ?: return null
        val primaryConstructor = decl.primaryConstructor ?: return null
        val param = primaryConstructor.parameters.firstOrNull() ?: return null
        val prop = decl.getAllProperties().find { it.simpleName.asString() == param.name?.asString() } ?: return null
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
        private const val GHOST_IGNORE = "GhostIgnore"
        private const val GHOST_NAME = "GhostName"
        private const val GHOST_SERIALIZATION = "GhostSerialization"
        private const val NAME_ARG = "name"
        private const val LIST_QUALIFIED = "kotlin.collections.List"
        private const val MAP_QUALIFIED = "kotlin.collections.Map"
        private const val STRING_QUALIFIED = "kotlin.String"
        private val PRIMITIVE_ARRAYS = setOf(
            "kotlin.IntArray",
            "kotlin.LongArray",
            "kotlin.FloatArray",
            "kotlin.DoubleArray",
            "kotlin.BooleanArray"
        )
    }
}