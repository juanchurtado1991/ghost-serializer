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
    private val bufferedSink: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val discriminator: String? = null
) {

    fun build(): FunSpec {
        val code = CodeBlock.builder()
        
        when {
            isSealed -> emitSealedDispatch(code)
            isValue -> emitValueUnboxing(code)
            else -> {
                code.addStatement("writer.beginObject()")
                if (discriminator != null) {
                    code.addStatement("writer.name(%S).value(%S)", "type", discriminator)
                }
                properties.forEach { prop -> emitProperty(code, prop) }
                code.addStatement("writer.endObject()")
            }
        }

        return FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("writer", writerClass)
            .addParameter("value", originalClassName)
            .addCode(code.build())
            .build()
    }

    private fun emitSealedDispatch(code: CodeBlock.Builder) {
        code.beginControlFlow("when (value)")
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(subClassName.packageName, "${subClassName.simpleName}Serializer")
            code.addStatement("is %T -> %T.serialize(writer, value)", subClassName, serializerName)
        }
        code.endControlFlow()
    }

    private fun emitValueUnboxing(code: CodeBlock.Builder) {
        // Find the single property of the value class
        val prop = properties.firstOrNull() ?: return
        val accessor = "value.${prop.kotlinName}"
        emitValue(code, prop, accessor)
    }

    private fun emitProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        val accessor = "value.${prop.kotlinName}"
        val nameIndex = properties.indexOf(prop)

        if (prop.isNullable) {
            code.beginControlFlow("if ($accessor != null)")
            code.addStatement("writer.name(OPTIONS.byteStrings[$nameIndex])")
            emitValue(code, prop, accessor)
            code.nextControlFlow("else")
            code.addStatement("writer.name(OPTIONS.byteStrings[$nameIndex]).nullValue()")
            code.endControlFlow()
            return
        }

        code.addStatement("writer.name(OPTIONS.byteStrings[$nameIndex])")
        emitValue(code, prop, accessor)
    }

    private fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                val innerAccessor = "$accessor.${prop.valueClassProperty!!.kotlinName}"
                emitValue(code, prop.valueClassProperty!!, innerAccessor)
            }
            prop.isSealedClass -> {
                code.addStatement("%T.serialize(writer, $accessor)", serializerName(prop.type))
            }
            prop.isEnum -> code.addStatement("writer.value($accessor.name)")
            prop.type.isPrimitive() -> code.addStatement("writer.value($accessor)")
            prop.isGhost -> emitGhost(code, prop, accessor)
            prop.isPrimitiveArray -> code.addStatement(
                "%T.serialize(writer, $accessor)",
                ClassName("com.ghost.serialization.core", "${prop.primitiveArrayType}Serializer")
            )
            prop.isList -> emitList(code, prop, accessor)
            prop.isMap -> emitMap(code, prop, accessor)
            else -> code.addStatement("writer.value($accessor)")
        }
    }

    private fun emitGhost(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement(
            "%T.serialize(writer, $accessor)",
            serializerName(prop.type)
        )
    }

    private fun emitList(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement("writer.beginArray()")
        code.beginControlFlow("for (item in $accessor)")

        when {
            prop.listInnerIsGhost -> code.addStatement(
                "%T.serialize(writer, item)", serializerName(prop.listInnerType!!)
            )
            prop.listInnerIsEnum -> code.addStatement("writer.value(item.name)")
            else -> code.addStatement("writer.value(item)")
        }

        code.endControlFlow()
        code.addStatement("writer.endArray()")
    }

    private fun emitMap(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: String) {
        code.addStatement("writer.beginObject()")
        code.beginControlFlow("for ((mapKey, mapVal) in $accessor)")
        code.addStatement("writer.name(mapKey)")

        when {
            prop.mapValueIsGhost -> code.addStatement(
                "%T.serialize(writer, mapVal)", serializerName(prop.mapValueType!!)
            )
            else -> code.addStatement("writer.value(mapVal)")
        }

        code.endControlFlow()
        code.addStatement("writer.endObject()")
    }

    private fun serializerName(type: KSType): ClassName {
        val decl = type.declaration
        return ClassName(decl.packageName.asString(), "${decl.simpleName.asString()}Serializer")
    }
}
