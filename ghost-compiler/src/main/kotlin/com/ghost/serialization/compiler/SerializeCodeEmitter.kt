@file:Suppress("SameParameterValue")

package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

internal class SerializeCodeEmitter(
    private val properties: List<GhostPropertyModel>,
    private val originalClassName: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val discriminator: String? = null,
    private val sealedDiscriminatorKey: String = C.DEFAULT_DISCRIMINATOR_KEY
) {
    private val sortedProperties = run {
        val rootToFirstIndex = mutableMapOf<String, Int>()
        properties.forEachIndexed { index, prop ->
            val root = prop.flattenPath?.firstOrNull() ?: prop.wrapPath?.firstOrNull() ?: prop.jsonName
            rootToFirstIndex.putIfAbsent(root, index)
        }

        properties.sortedWith { p1, p2 ->
            val root1 = p1.flattenPath?.firstOrNull() ?: p1.wrapPath?.firstOrNull() ?: p1.jsonName
            val root2 = p2.flattenPath?.firstOrNull() ?: p2.wrapPath?.firstOrNull() ?: p2.jsonName

            val index1 = rootToFirstIndex[root1]!!
            val index2 = rootToFirstIndex[root2]!!

            if (index1 != index2) {
                index1.compareTo(index2)
            } else {
                // Same root, sort by full path to ensure grouping works for nested flattened fields
                val path1 = (p1.flattenPath?.joinToString(C.STR_DOT) ?: p1.jsonName)
                val path2 = (p2.flattenPath?.joinToString(C.STR_DOT) ?: p2.jsonName)
                path1.compareTo(path2)
            }
        }
    }

    private val contextualSerializers = mutableMapOf<KSType, String>()

    fun build(writerClass: ClassName, typeSpecBuilder: TypeSpec.Builder): FunSpec {
        val code = CodeBlock.builder()

        when {
            isSealed -> emitSealedDispatch(code)
            isValue -> emitValueUnboxing(code)
            isEnum -> emitEnumSerialization(code)

            properties.size > C.DEFAULT_CHUNK_SIZE -> {
                val fragmented =
                    FragmentedSerializeEmitter(sortedProperties, originalClassName, writerClass, this)
                fragmented.emit(code, typeSpecBuilder, discriminator, sealedDiscriminatorKey)
            }

            else -> {
                code.addStatement(C.STR_WRITER_BEGIN_OBJ)

                if (discriminator != null) {
                    code.addStatement(
                        C.STR_WRITER_NAME_TYPE_VAL,
                        sealedDiscriminatorKey,
                        discriminator
                    )
                }

                val currentPath = mutableListOf<String>()
                sortedProperties.forEach { prop ->
                    val targetPath = prop.flattenPath?.dropLast(1) 
                        ?: prop.wrapPath 
                        ?: emptyList()

                    // Close objects that are not in the new target path
                    while (currentPath.isNotEmpty() && !isPrefix(currentPath, targetPath)) {
                        code.addStatement(C.STR_WRITER_END_OBJ)
                        currentPath.removeAt(currentPath.size - 1)
                    }

                    // Open new objects in the target path
                    targetPath.drop(currentPath.size).forEach { segment ->
                        code.addStatement(C.STR_WRITER_WRITE_NAME_VAL, C.STR_H_VAL_PREFIX + segment.uppercase())
                        code.addStatement(C.STR_WRITER_BEGIN_OBJ)
                        currentPath.add(segment)
                    }

                    emitProperty(code, prop)
                }

                // Close remaining open objects
                while (currentPath.isNotEmpty()) {
                    code.addStatement(C.STR_WRITER_END_OBJ)
                    currentPath.removeAt(currentPath.size - 1)
                }
                code.addStatement(C.STR_WRITER_END_OBJ)
            }
        }

        return FunSpec.builder(C.STR_FUN_SERIALIZE)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(C.STR_PARAM_WRITER, writerClass)
            .addParameter(C.STR_PARAM_VALUE, originalClassName)
            .addCode(code.build())
            .build()
    }

    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(
            C.STR_GHOST_PKG,
            C.STR_GHOST_OBJ
        )

        contextualSerializers.forEach { (type, name) ->
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    name,
                    ClassName(
                        C.STR_CONTRACT_PKG,
                        C.STR_GHOST_SERIALIZER
                    ).parameterizedBy(type.toTypeName()),
                    KModifier.PRIVATE
                )
                    .initializer(
                        C.TEMPLATE_RESOLVE_SERIALIZER,
                        ghostClass,
                        type.toTypeName()
                    )
                    .build()
            )
        }
    }


    private fun emitEnumSerialization(code: CodeBlock.Builder) {
        val enumValues = properties.firstOrNull()?.enumValues
        if (enumValues != null) {
            code.beginControlFlow(C.STR_WHEN_VALUE)
            enumValues.forEach { (kotlinName, serialName) ->
                code.addStatement(
                    C.STR_ENUM_MEMBER_VAL,
                    originalClassName,
                    kotlinName,
                    serialName
                )
            }
            code.endControlFlow()
        } else {
            code.addStatement(C.STR_WRITER_VAL_ENUM_NAME)
        }
    }

    private fun emitSealedDispatch(code: CodeBlock.Builder) {
        code.beginControlFlow(C.STR_WHEN_VALUE)
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                subClassName
                    .simpleNames
                    .joinToString(C.STR_UNDERSCORE)
                        + C.STR_SERIALIZER_SUFFIX
            )
            code.addStatement(
                C.STR_IS_T_ARROW_T_SERIALIZE,
                subClassName, serializerName
            )
        }
        code.endControlFlow()
    }

    private fun emitValueUnboxing(code: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val accessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)
        emitValue(code, prop, accessor)
    }

    private fun isFusedType(prop: GhostPropertyModel): Boolean {
        if (prop.customEncoder != null) return false
        val type = prop.type.declaration.qualifiedName?.asString()
        return when (type) {
            C.K_INT,
            C.K_LONG,
            C.K_STRING,
            C.K_BOOLEAN,
            C.K_DOUBLE,
            C.K_FLOAT -> true

            else -> false
        }
    }

    internal fun emitProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        val cleanName = prop.jsonName.replace(C.STR_DOT, C.STR_UNDERSCORE).uppercase()
        val headerName = C.STR_H_VAL_PREFIX + cleanName
        val accessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)

        if (prop.isNullable) {
            if (prop.hasDefaultValue) {
                code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                val canUseFused = isFusedType(prop) && !prop.isContextual && prop.customEncoder == null
                if (canUseFused) {
                    code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
                } else {
                    code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                    emitValue(code, prop, accessor)
                }
                code.endControlFlow()
            } else {
                val canUseFused = isFusedType(prop) && !prop.isContextual && prop.customEncoder == null
                if (canUseFused) {
                    code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                    code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
                    code.nextControlFlow(C.STR_ELSE)
                    code.addStatement(C.STR_WRITE_NAME_RAW_NULL, headerName)
                    code.endControlFlow()
                } else {
                    code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                    code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                    emitValue(code, prop, accessor)
                    code.nextControlFlow(C.STR_ELSE)
                    code.addStatement(C.STR_WRITER_NULL_VAL)
                    code.endControlFlow()
                }
            }
            return
        }

        val canUseFused = isFusedType(prop) && !prop.isContextual && prop.customEncoder == null
        if (canUseFused) {
            code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
        } else {
            code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
            emitValue(code, prop, accessor)
        }
    }

    private fun isPrefix(prefix: List<String>, full: List<String>): Boolean {
        if (prefix.size > full.size) return false
        for (i in prefix.indices) {
            if (prefix[i] != full[i]) return false
        }
        return true
    }

    private fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: Any) {
        if (prop.customEncoder != null) {
            code.addStatement(
                C.STR_CUSTOM_ENCODER_CALL,
                prop.customEncoder.provider,
                prop.customEncoder.functionName,
                accessor
            )
            return
        }
        when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                val innerAccessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, accessor, prop.valueClassProperty.kotlinName)
                emitValue(code, prop.valueClassProperty, innerAccessor)
            }

            prop.isSealedClass -> {
                code.addStatement(
                    C.STR_T_SERIALIZE_WRITER_ACC,
                    serializerName(prop.type),
                    accessor
                )
            }

            prop.isPrimitiveArray -> code.addStatement(
                C.STR_T_SERIALIZE_WRITER_ACC,
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    prop.primitiveArrayType + C.STR_SERIALIZER_SUFFIX
                ),
                accessor
            )

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }

            else -> emitTypeValue(code, prop.type, accessor, 0, skipNullCheck = true)
        }
    }

    private fun emitTypeValue(code: CodeBlock.Builder, type: KSType, accessor: Any, depth: Int, skipNullCheck: Boolean = false) {
        val isNullable = type.isMarkedNullable
        if (isNullable && !skipNullCheck) {
            code.beginControlFlow(C.TEMPLATE_IF_NULL, accessor)
            code.addStatement(C.STR_NULL_VAL_CALL)
            code.nextControlFlow(C.STR_ELSE)
        }

        val typeName = type.declaration.qualifiedName?.asString()
        when {
            type.isGhost() -> code.addStatement(
                C.STR_T_SERIALIZE_WRITER_ACC,
                serializerName(type),
                accessor
            )

            type.isEnum() -> code.addStatement(
                C.STR_T_SERIALIZE_WRITER_ACC,
                serializerName(type),
                accessor
            )

            typeName == C.K_INT -> code.addStatement(C.STR_WRITER_VAL_L, accessor)
            typeName == C.K_LONG -> code.addStatement(C.STR_WRITER_VAL_L, accessor)
            typeName == C.K_STRING -> code.addStatement(C.STR_WRITER_VAL_L, accessor)
            typeName == C.K_BOOLEAN -> code.addStatement(C.STR_WRITER_VAL_L, accessor)
            typeName == C.K_DOUBLE -> code.addStatement(C.STR_WRITER_VAL_L, accessor)
            typeName == C.K_FLOAT -> code.addStatement(C.STR_WRITER_VAL_FLOAT, accessor)
            type.isList() -> emitList(code, type, accessor, depth)
            type.isMap() -> emitMap(code, type, accessor, depth)

            else -> {
                val name = getContextualSerializerName(type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }
        }

        if (isNullable && !skipNullCheck) {
            code.endControlFlow()
        }
    }

    private fun emitList(code: CodeBlock.Builder, type: KSType, accessor: Any, depth: Int) {
        val itemName = C.STR_ITEM_PREFIX + depth
        code.addStatement(C.STR_WRITER_BEGIN_ARR)
        code.beginControlFlow(C.TEMPLATE_FOR_IN, itemName, accessor)
        val innerType = type.arguments.firstOrNull()?.type?.resolve()
        if (innerType != null) {
            emitTypeValue(code, innerType, itemName, depth + 1, skipNullCheck = false)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, itemName)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_ARR)
    }

    private fun emitMap(code: CodeBlock.Builder, type: KSType, accessor: Any, depth: Int) {
        val keyName = C.STR_MAP_KEY_PREFIX + depth
        val valName = C.STR_MAP_VAL_PREFIX + depth
        code.addStatement(C.STR_WRITER_BEGIN_OBJ)
        code.beginControlFlow(C.TEMPLATE_FOR_MAP, keyName, valName, accessor)
        code.addStatement(C.TEMPLATE_WRITER_NAME, keyName)
        val valueType = type.arguments.getOrNull(1)?.type?.resolve()
        if (valueType != null) {
            emitTypeValue(code, valueType, valName, depth + 1, skipNullCheck = false)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, valName)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    private fun getContextualSerializerName(type: KSType): String {
        return contextualSerializers.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            C.STR_CONTEXTUAL_PREFIX +
                    simpleName.replaceFirstChar { it.lowercase() } +
                    C.STR_SERIALIZER_SUFFIX
        }
    }

    private fun serializerName(type: KSType): ClassName =
        with(type.declaration as KSClassDeclaration) {
            val className = toClassName()
            return ClassName(
                className.packageName,
                className.simpleNames.joinToString(C.STR_UNDERSCORE)
                        + C.STR_SERIALIZER_SUFFIX
            )
        }
}
