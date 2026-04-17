package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName

internal class SerializeCodeEmitter(
    private val properties: List<GhostPropertyModel>,
    private val originalClassName: ClassName,
    private val writerClass: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val discriminator: String? = null
) {

    fun build(): FunSpec {
        val code = CodeBlock.builder()

        when {
            isSealed -> emitSealedDispatch(code)
            isValue -> emitValueUnboxing(code)
            isEnum -> emitEnumSerialization(code)
            else -> {
                code.addStatement(STR_WRITER_BEGIN_OBJ)
                if (discriminator != null) {
                    code.addStatement(STR_WRITER_NAME_TYPE_VAL, STR_TYPE_KEY, discriminator)
                }
                properties.forEach { prop -> emitProperty(code, prop) }
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

    private fun emitEnumSerialization(code: CodeBlock.Builder) {
        val enumValues = properties.firstOrNull()?.enumValues
        if (enumValues != null) {
            code.beginControlFlow("when (value)")
            enumValues.forEach { (kotlinName, serialName) ->
                code.addStatement("%T.$kotlinName -> writer.value(%S)", originalClassName, serialName)
            }
            code.endControlFlow()
        } else {
            code.addStatement("writer.value(value.name)")
        }
    }

    private fun emitSealedDispatch(code: CodeBlock.Builder) {
        code.beginControlFlow(STR_WHEN_VALUE)
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                "${subClassName.simpleNames.joinToString("_")}$STR_SERIALIZER_SUFFIX"
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
        val accessor = "$STR_VALUE_DOT${prop.kotlinName}"
        emitValue(code, prop, accessor)
    }

    private fun emitProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        val accessor = "$STR_VALUE_DOT${prop.kotlinName}"
        val nameIndex = properties.indexOf(prop)

        if (prop.isNullable) {
            code.beginControlFlow(STR_IF_ACCESSOR_NOT_NULL, accessor)
            code.addStatement(STR_WRITER_NAME_OPTIONS_INDEX, nameIndex)
            emitValue(code, prop, accessor)
            code.endControlFlow()
            return
        }

        code.addStatement(STR_WRITER_NAME_OPTIONS_INDEX, nameIndex)
        emitValue(code, prop, accessor)
    }

    private fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                val innerAccessor = "$accessor$STR_DOT${prop.valueClassProperty!!.kotlinName}"
                emitValue(code, prop.valueClassProperty!!, innerAccessor)
            }

            prop.isSealedClass -> {
                code.addStatement(STR_T_SERIALIZE_WRITER_ACC, serializerName(prop.type), accessor)
            }

            prop.isEnum -> code.addStatement(STR_T_SERIALIZE_WRITER_ACC, serializerName(prop.type), accessor)
            prop.type.isPrimitive() -> code.addStatement(STR_WRITER_VALUE_ACC, accessor)
            prop.isGhost -> emitGhost(code, prop, accessor)
            prop.isPrimitiveArray -> code.addStatement(
                STR_T_SERIALIZE_WRITER_ACC,
                ClassName(STR_SERIALIZERS_PKG, "${prop.primitiveArrayType}$STR_SERIALIZER_SUFFIX"),
                accessor
            )

            prop.isList -> emitList(code, prop, accessor)
            prop.isMap -> emitMap(code, prop, accessor)
            else -> code.addStatement(STR_WRITER_VALUE_ACC, accessor)
        }
    }

    private fun emitGhost(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement(STR_T_SERIALIZE_WRITER_ACC, serializerName(prop.type), accessor)
    }

    private fun emitList(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement(STR_WRITER_BEGIN_ARR)
        code.beginControlFlow(STR_FOR_ITEM_IN_ACC, accessor)

        when {
            prop.listInnerIsGhost -> code.addStatement(
                STR_T_SERIALIZE_WRITER_ITEM, serializerName(prop.listInnerType!!)
            )

            prop.listInnerIsEnum -> code.addStatement(STR_T_SERIALIZE_WRITER_ITEM, serializerName(prop.listInnerType!!))
            else -> code.addStatement(STR_WRITER_VALUE_ITEM)
        }

        code.endControlFlow()
        code.addStatement(STR_WRITER_END_ARR)
    }

    private fun emitMap(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement(STR_WRITER_BEGIN_OBJ)
        code.beginControlFlow(STR_FOR_MAP_IN_ACC, accessor)
        code.addStatement(STR_WRITER_NAME_MAPKEY)

        when {
            prop.mapValueIsGhost -> code.addStatement(
                STR_T_SERIALIZE_WRITER_MAPVAL, serializerName(prop.mapValueType!!)
            )

            else -> code.addStatement(STR_WRITER_VALUE_MAPVAL)
        }

        code.endControlFlow()
        code.addStatement(STR_WRITER_END_OBJ)
    }

    private fun serializerName(type: KSType): ClassName = with(type.declaration as KSClassDeclaration) {
        val className = toClassName()
        return ClassName(className.packageName, "${className.simpleNames.joinToString("_")}$STR_SERIALIZER_SUFFIX")
    }

    companion object {
        private const val STR_WRITER_BEGIN_OBJ = "writer.beginObject()"
        private const val STR_WRITER_NAME_TYPE_VAL = "writer.name(%S).value(%S)"
        private const val STR_TYPE_KEY = "type"
        private const val STR_WRITER_END_OBJ = "writer.endObject()"
        private const val STR_FUN_SERIALIZE = "serialize"
        private const val STR_PARAM_WRITER = "writer"
        private const val STR_PARAM_VALUE = "value"
        private const val STR_WHEN_VALUE = "when (value)"
        private const val STR_SERIALIZER_SUFFIX = "Serializer"
        private const val STR_IS_T_ARROW_T_SERIALIZE = "is %T -> %T.serialize(writer, value)"
        private const val STR_VALUE_DOT = "value."
        private const val STR_IF_ACCESSOR_NOT_NULL = "if (%L != null)"
        private const val STR_WRITER_NAME_OPTIONS_INDEX = "writer.writeName(%L, OPTIONS)"
        private const val STR_ELSE = "else"
        private const val STR_WRITER_NAME_OPTIONS_NULL =
            "writer.name(OPTIONS.byteStrings[%L]).nullValue()"
        private const val STR_DOT = "."
        private const val STR_T_SERIALIZE_WRITER_ACC = "%T.serialize(writer, %L)"
        private const val STR_WRITER_VALUE_VALUE_NAME = "writer.value(value.name)"
        private const val STR_WRITER_VALUE_ACC_NAME = "writer.value(%L.name)"
        private const val STR_WRITER_VALUE_ACC = "writer.value(%L)"
        private const val STR_SERIALIZERS_PKG = "com.ghost.serialization.serializers"
        private const val STR_WRITER_BEGIN_ARR = "writer.beginArray()"
        private const val STR_FOR_ITEM_IN_ACC = "for (item in %L)"
        private const val STR_T_SERIALIZE_WRITER_ITEM = "%T.serialize(writer, item)"
        private const val STR_WRITER_VALUE_ITEM_NAME = "writer.value(item.name)"
        private const val STR_WRITER_VALUE_ITEM = "writer.value(item)"
        private const val STR_WRITER_END_ARR = "writer.endArray()"
        private const val STR_FOR_MAP_IN_ACC = "for ((mapKey, mapVal) in %L)"
        private const val STR_WRITER_NAME_MAPKEY = "writer.name(mapKey)"
        private const val STR_T_SERIALIZE_WRITER_MAPVAL = "%T.serialize(writer, mapVal)"
        private const val STR_WRITER_VALUE_MAPVAL = "writer.value(mapVal)"
    }
}
