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

    private val contextualSerializers = mutableMapOf<KSType, String>()

    fun build(writerClass: ClassName): FunSpec {
        val code = CodeBlock.builder()

        when {
            isSealed -> emitSealedDispatch(code)
            isValue -> emitValueUnboxing(code)
            isEnum -> emitEnumSerialization(code)
            else -> {
                val firstProp = properties.firstOrNull()
                val hasDiscriminator = discriminator != null

                // No local variable lookups in hot-path anymore

                if (firstProp != null && !firstProp.isNullable && !hasDiscriminator && isFusedType(
                        firstProp
                    )
                ) {
                    emitFirstProperty(code, firstProp, 0)
                    var allPrecedingNonNullable = true
                    properties.forEachIndexed { idx, prop ->
                        if (idx > 0) {
                            if (!prop.isNullable && allPrecedingNonNullable) {
                                emitPropertyWithComma(code, prop, idx)
                            } else {
                                emitProperty(code, prop, idx)
                            }
                        }
                        if (prop.isNullable) allPrecedingNonNullable = false
                    }
                } else {
                    code.addStatement(STR_WRITER_BEGIN_OBJ)
                    if (hasDiscriminator) {
                        code.addStatement(
                            STR_WRITER_NAME_TYPE_VAL,
                            sealedDiscriminatorKey,
                            discriminator
                        )
                    }
                    properties.forEachIndexed { idx, prop ->
                        emitProperty(code, prop, idx)
                    }
                }
                code.addStatement(STR_WRITER_END_OBJ)
            }
        }

        return FunSpec.builder(STR_FUN_SERIALIZE)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(STR_PARAM_WRITER, writerClass)
            .addParameter(STR_PARAM_VALUE, originalClassName)
            .addCode(code.build())
            .build()
    }

    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(C.STR_GHOST_PKG, C.STR_GHOST_OBJ)
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
                    .initializer(C.TEMPLATE_RESOLVE_SERIALIZER, ghostClass, type.toTypeName())
                    .build()
            )
        }
    }

    fun injectHeaderConstants(typeSpecBuilder: TypeSpec.Builder) {
        if (properties.isEmpty() || isValue || isEnum || isSealed) return

        // Cache first header
        typeSpecBuilder.addProperty(
            PropertySpec.builder(STR_H_FIRST + 0, C.BYTE_STRING_CLASS, KModifier.PRIVATE)
                .initializer("%N.writerFirstHeaders[0]", STR_OPTIONS_NAME)
                .build()
        )

        // Cache headers with comma
        properties.forEachIndexed { idx, _ ->
            if (idx > 0) {
                typeSpecBuilder.addProperty(
                    PropertySpec.builder(STR_H_COMMA + idx, C.BYTE_STRING_CLASS, KModifier.PRIVATE)
                        .initializer("%N.writerHeadersWithComma[%L]", STR_OPTIONS_NAME, idx)
                        .build()
                )
            }
        }
        
        // Cache standard headers (for nullable or mixed paths)
        properties.forEachIndexed { idx, _ ->
             typeSpecBuilder.addProperty(
                PropertySpec.builder(STR_H_STD + idx, C.BYTE_STRING_CLASS, KModifier.PRIVATE)
                    .initializer("%N.writerHeaders[%L]", STR_OPTIONS_NAME, idx)
                    .build()
            )
        }
    }

    private fun emitEnumSerialization(code: CodeBlock.Builder) {
        val enumValues = properties.firstOrNull()?.enumValues
        if (enumValues != null) {
            code.beginControlFlow(STR_WHEN_VALUE)
            enumValues.forEach { (kotlinName, serialName) ->
                code.addStatement(STR_ENUM_MEMBER_VAL, originalClassName, kotlinName, serialName)
            }
            code.endControlFlow()
        } else {
            code.addStatement(STR_WRITER_VAL_ENUM_NAME)
        }
    }

    private fun emitSealedDispatch(code: CodeBlock.Builder) {
        code.beginControlFlow(STR_WHEN_VALUE)
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                subClassName.simpleNames.joinToString(STR_UNDERSCORE) + STR_SERIALIZER_SUFFIX
            )
            code.addStatement(
                STR_IS_T_ARROW_T_SERIALIZE,
                subClassName, serializerName
            )
        }
        code.endControlFlow()
    }

    private fun emitValueUnboxing(code: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val accessor = STR_VALUE_DOT + prop.kotlinName
        emitValue(code, prop, accessor)
    }

    private fun isFusedType(prop: GhostPropertyModel): Boolean {
        if (prop.customEncoder != null) return false
        val type = prop.type.declaration.qualifiedName?.asString()
        return when (type) {
            K_INT, K_LONG, K_STRING, K_BOOLEAN, K_DOUBLE, K_FLOAT -> true
            else -> false
        }
    }

    private fun emitFirstProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        nameIndex: Int
    ) {
        val canUseFused = isFusedType(prop)

        if (canUseFused) {
            code.addStatement(STR_WRITER_WRITE_FIRST, STR_H_FIRST + nameIndex, prop.kotlinName)
        } else {
            code.addStatement(STR_WRITER_WRITE_NAME_RAW, STR_H_FIRST + nameIndex)
            emitValue(code, prop, STR_VALUE_DOT + prop.kotlinName)
        }
    }

    private fun emitPropertyWithComma(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        nameIndex: Int
    ) {
        if (isFusedType(prop)) {
            code.addStatement(STR_WRITER_WRITE_FIELD_COMMA, STR_H_COMMA + nameIndex, prop.kotlinName)
        } else {
            code.addStatement(STR_WRITER_WRITE_NAME_RAW_COMMA, STR_H_COMMA + nameIndex)
            emitValue(code, prop, STR_VALUE_DOT + prop.kotlinName)
        }
    }

    private fun emitProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        nameIndex: Int
    ) {
        val accessor = STR_VALUE_DOT + prop.kotlinName
        val canUseFused = isFusedType(prop)

        if (prop.isNullable) {
            code.beginControlFlow(STR_IF_ACCESSOR_NOT_NULL, accessor)
            if (canUseFused) {
                code.addStatement(STR_WRITER_WRITE_FIELD, STR_H_STD + nameIndex, prop.kotlinName)
            } else {
                code.addStatement(STR_WRITER_WRITE_NAME_RAW, STR_H_STD + nameIndex)
                emitValue(code, prop, accessor)
            }
            code.endControlFlow()
            return
        }

        if (canUseFused) {
            code.addStatement(STR_WRITER_WRITE_FIELD, STR_H_STD + nameIndex, prop.kotlinName)
        } else {
            code.addStatement(STR_WRITER_WRITE_NAME_RAW, STR_H_STD + nameIndex)
            emitValue(code, prop, accessor)
        }
    }

    private fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        if (prop.customEncoder != null) {
            code.addStatement(STR_CUSTOM_ENCODER_CALL, prop.customEncoder, accessor)
            return
        }
        when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                val innerAccessor = accessor + STR_DOT + prop.valueClassProperty.kotlinName
                emitValue(code, prop.valueClassProperty, innerAccessor)
            }

            prop.isSealedClass -> {
                code.addStatement(
                    STR_T_SERIALIZE_WRITER_ACC,
                    serializerName(prop.type),
                    accessor
                )
            }

            prop.isPrimitiveArray -> code.addStatement(
                STR_T_SERIALIZE_WRITER_ACC,
                ClassName(
                    STR_SERIALIZERS_PKG,
                    prop.primitiveArrayType + STR_SERIALIZER_SUFFIX
                ),
                accessor
            )

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                code.addStatement(STR_SERIALIZE_CALL, name, accessor)
            }

            else -> emitTypeValue(code, prop.type, accessor)
        }
    }

    private fun emitTypeValue(code: CodeBlock.Builder, type: KSType, accessor: String) {
        val typeName = type.declaration.qualifiedName?.asString()
        when {
            type.isGhost() -> code.addStatement(
                STR_T_SERIALIZE_WRITER_ACC,
                serializerName(type),
                accessor
            )

            type.isEnum() -> code.addStatement(
                STR_T_SERIALIZE_WRITER_ACC,
                serializerName(type),
                accessor
            )

            typeName == K_INT -> code.addStatement(STR_WRITER_VAL_L, accessor)
            typeName == K_LONG -> code.addStatement(STR_WRITER_VAL_L, accessor)
            typeName == K_STRING -> code.addStatement(STR_WRITER_VAL_L, accessor)
            typeName == K_BOOLEAN -> code.addStatement(STR_WRITER_VAL_L, accessor)
            typeName == K_DOUBLE -> code.addStatement(STR_WRITER_VAL_L, accessor)
            typeName == K_FLOAT -> code.addStatement(STR_WRITER_VAL_FLOAT, accessor)
            type.isList() -> emitList(code, type, accessor)
            type.isMap() -> emitMap(code, type, accessor)
            else -> {
                val name = getContextualSerializerName(type)
                code.addStatement(STR_SERIALIZE_CALL, name, accessor)
            }
        }
    }

    private fun emitList(code: CodeBlock.Builder, type: KSType, accessor: String) {
        code.addStatement(STR_WRITER_BEGIN_ARR)
        code.beginControlFlow(STR_FOR_ITEM_IN_ACC, accessor)
        val innerType = type.arguments.firstOrNull()?.type?.resolve()
        if (innerType != null) {
            emitTypeValue(code, innerType, STR_VAR_ITEM)
        } else {
            code.addStatement(STR_WRITER_VALUE_ITEM)
        }
        code.endControlFlow()
        code.addStatement(STR_WRITER_END_ARR)
    }

    private fun emitMap(code: CodeBlock.Builder, type: KSType, accessor: String) {
        code.addStatement(STR_WRITER_BEGIN_OBJ)
        code.beginControlFlow(STR_FOR_MAP_IN_ACC, accessor)
        code.addStatement(STR_WRITER_NAME_MAPKEY)
        val valueType = type.arguments.getOrNull(1)?.type?.resolve()
        if (valueType != null) {
            emitTypeValue(code, valueType, STR_VAR_MAP_VAL)
        } else {
            code.addStatement(STR_WRITER_VALUE_MAPVAL)
        }
        code.endControlFlow()
        code.addStatement(STR_WRITER_END_OBJ)
    }

    private fun getContextualSerializerName(type: KSType): String {
        return contextualSerializers.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            STR_CONTEXTUAL_PREFIX +
                    simpleName.replaceFirstChar { it.lowercase() } +
                    STR_SERIALIZER_SUFFIX
        }
    }

    private fun serializerName(type: KSType): ClassName =
        with(type.declaration as KSClassDeclaration) {
            val className = toClassName()
            return ClassName(
                className.packageName,
                className.simpleNames.joinToString(STR_UNDERSCORE)
                        + STR_SERIALIZER_SUFFIX
            )
        }

    companion object {
        private const val STR_WRITER_BEGIN_OBJ = "writer.beginObject()"
        private const val STR_WRITER_NAME_TYPE_VAL = "writer.name(%S).value(%S)"
        private const val STR_WRITER_END_OBJ = "writer.endObject()"
        private const val STR_FUN_SERIALIZE = "serialize"
        private const val STR_PARAM_WRITER = "writer"
        private const val STR_PARAM_VALUE = "value"
        private const val STR_WHEN_VALUE = "when (value)"
        private const val STR_SERIALIZER_SUFFIX = "Serializer"
        private const val STR_IS_T_ARROW_T_SERIALIZE = "is %T -> %T.serialize(writer, value)"
        private const val STR_VALUE_DOT = "value."
        private const val STR_IF_ACCESSOR_NOT_NULL = "if (%L != null)"
        private const val STR_DOT = "."
        private const val STR_UNDERSCORE = "_"
        private const val STR_T_SERIALIZE_WRITER_ACC = "%T.serialize(writer, %L)"
        private const val STR_SERIALIZERS_PKG = "com.ghost.serialization.serializers"
        private const val STR_WRITER_BEGIN_ARR = "writer.beginArray()"
        private const val STR_FOR_ITEM_IN_ACC = "for (item in %L)"
        private const val STR_WRITER_VALUE_ITEM = "writer.value(item)"
        private const val STR_WRITER_END_ARR = "writer.endArray()"
        private const val STR_FOR_MAP_IN_ACC = "for ((mapKey, mapVal) in %L)"
        private const val STR_WRITER_NAME_MAPKEY = "writer.name(mapKey)"
        private const val STR_WRITER_VALUE_MAPVAL = "writer.value(mapVal)"

        private const val STR_HEADERS_INIT = "val headers = OPTIONS.writerHeaders"
        private const val STR_FIRST_HEADERS_INIT = "val firstHeaders = OPTIONS.writerFirstHeaders"
        private const val STR_HEADERS_COMMA_INIT =
            "val headersWithComma = OPTIONS.writerHeadersWithComma"
        private const val STR_VAR_HEADERS = "headers"
        private const val STR_VAR_FIRST_HEADERS = "firstHeaders"
        private const val STR_VAR_HEADERS_COMMA = "headersWithComma"

        private const val STR_ENUM_MEMBER_VAL = "%T.%L -> writer.value(%S)"
        private const val STR_WRITER_VAL_ENUM_NAME = "writer.value(value.name)"

        private const val K_INT = "kotlin.Int"
        private const val K_LONG = "kotlin.Long"
        private const val K_STRING = "kotlin.String"
        private const val K_BOOLEAN = "kotlin.Boolean"
        private const val K_DOUBLE = "kotlin.Double"
        private const val K_FLOAT = "kotlin.Float"

        private const val STR_WRITER_WRITE_FIRST = "writer.writeFirstField(%L, value.%L)"
        private const val STR_WRITER_WRITE_NAME_RAW = "writer.writeNameRaw(%L)"
        private const val STR_WRITER_WRITE_FIELD_COMMA = "writer.writeFieldWithComma(%L, value.%L)"
        private const val STR_WRITER_WRITE_NAME_RAW_COMMA = "writer.writeNameRawWithComma(%L)"
        private const val STR_WRITER_WRITE_FIELD = "writer.writeField(%L, value.%L)"
        
        private const val STR_H_FIRST = "STR_H_F_"
        private const val STR_H_COMMA = "STR_H_C_"
        private const val STR_H_STD = "STR_H_S_"
        private const val STR_OPTIONS_NAME = "OPTIONS"
        private const val STR_SERIALIZE_CALL = "%L.serialize(writer, %L)"
        private const val STR_WRITER_VAL_L = "writer.value(%L)"
        private const val STR_WRITER_VAL_FLOAT = "writer.value(%L.toDouble())"

        private const val STR_CUSTOM_ENCODER_CALL = "%L(writer, %L)"
        private const val STR_VAR_ITEM = "item"
        private const val STR_VAR_MAP_VAL = "mapVal"
        private const val STR_CONTEXTUAL_PREFIX = "contextual_"
    }
}
