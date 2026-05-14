package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main coordinator for generating deserialization code.
 *
 * Orchestrates the code generation process by delegating to specialized emitters
 * ([StandardEmitter], [FragmentedEmitter]) or handling simple cases directly
 * (Sealed, Value, Enum).
 */
internal class DeserializeCodeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val discriminatorKey: String = "type"
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    fun build(typeSpecBuilder: TypeSpec.Builder) {
        val body = CodeBlock.builder()

        when {
            isSealed -> emitSealed(body)
            isValue -> emitValue(body)
            isEnum -> emitEnum(body)
            properties.size > 40 -> {
                FragmentedEmitter(properties, originalClassName, readerClass).emit(body, typeSpecBuilder)
                return
            }
            else -> {
                StandardEmitter(properties, originalClassName, readerClass).emit(body, typeSpecBuilder)
            }
        }

        addDeserializeFunction(typeSpecBuilder, body.build())
    }

    private fun addDeserializeFunction(typeSpecBuilder: TypeSpec.Builder, body: CodeBlock) {
        typeSpecBuilder.addFunction(
            FunSpec.builder(C.STR_DESERIALIZE)
                .addKdoc(C.STR_KDOC_DESERIALIZE, originalClassName)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(C.STR_READER, readerClass)
                .returns(originalClassName)
                .addCode(body)
                .build()
        )
    }

    private fun emitSealed(body: CodeBlock.Builder) {
        val fallbackSubclass = sealedSubclasses.find { subclass ->
            subclass.annotations.any { it.shortName.asString() == C.STR_FALLBACK_ANNOTATION }
        }
        val regularSubclasses = sealedSubclasses.filter { it != fallbackSubclass }

        body.addStatement(C.TEMPLATE_PEEK_TYPE, discriminatorKey, C.STR_MISSING_TYPE)
        body.beginControlFlow(C.STR_WHEN_TYPENAME)
        regularSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                "${subClassName.simpleNames.joinToString(C.STR_UNDERSCORE)}${C.STR_SERIALIZER}"
            )
            body.addStatement(C.TEMPLATE_DESERIALIZE_BRANCH, subClassName.simpleName, serializerName)
        }
        if (fallbackSubclass != null) {
            val fallbackClassName = fallbackSubclass.toClassName()
            val fallbackSerializerName = ClassName(
                fallbackClassName.packageName,
                "${fallbackClassName.simpleNames.joinToString(C.STR_UNDERSCORE)}${C.STR_SERIALIZER}"
            )
            body.beginControlFlow(C.STR_ELSE_BRANCH)
            body.addStatement(C.TEMPLATE_DESERIALIZE_T, fallbackSerializerName)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_UNKNOWN_TYPE)
        }
        body.endControlFlow()
        body.addStatement(C.STR_RETURN_RESULT)
    }

    private fun emitValue(body: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val call = buildCall(prop)
        body.addStatement(C.TEMPLATE_RETURN_CONSTRUCTOR, originalClassName, call)
    }

    private fun emitEnum(body: CodeBlock.Builder) {
        body.addStatement(C.STR_ENUM_SELECT_OPTIONS)
        body.beginControlFlow(C.STR_ENUM_WHEN)

        properties.firstOrNull()?.enumValues?.entries?.forEachIndexed { i, entry ->
            body.addStatement(C.TEMPLATE_ENUM_BRANCH, i, originalClassName, entry.key)
        }

        body.addStatement(C.STR_ERR_INVALID_ENUM_INDEX)
        body.addStatement(C.STR_ERR_UNEXPECTED_INDEX)
        body.endControlFlow()
    }
}
