package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

private val nullableAnyType = ClassName(C.PKG_KOTLIN, "Any").copy(nullable = true)
private val rawJsonType = ClassName(C.PKG_TYPES, C.STR_RAW_JSON_TYPE).copy(nullable = true)
private val ghostSerializerType = ClassName(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER)

/**
 * Emits zero-copy envelope routing helpers on generated serializer companions.
 */
internal class EnvelopeRouterEmitter(
    private val envelope: GhostEnvelopeModel,
    private val originalClassName: ClassName,
    private val flatReaderClass: ClassName
) {

    private val typedMappings = envelope.payloadMappings.filter { it.targetType != null }

    fun emit(typeSpecBuilder: TypeSpec.Builder) {
        emitCachedTargetSerializers(typeSpecBuilder)
        typeSpecBuilder.addFunction(buildRoutePayloadFunction())
        typeSpecBuilder.addFunction(buildParsePayloadFunction())

        if (typedMappings.isNotEmpty()) {
            typeSpecBuilder.addFunction(buildRouteTypedFunction())
            typeSpecBuilder.addFunction(buildParseTypedFunction())
        }
    }

    private fun emitCachedTargetSerializers(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(C.PKG_GHOST, C.STR_GHOST)
        typedMappings.forEach { mapping ->
            val targetType = mapping.targetType ?: return@forEach
            typeSpecBuilder.addProperty(
                PropertySpec.builder(targetSerializerPropertyName(mapping), ghostSerializerType.parameterizedBy(targetType))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.getSerializer(%T::class)!!", ghostClass, targetType)
                    .build()
            )
        }
    }

    private fun buildRoutePayloadFunction(): FunSpec {
        val builder = FunSpec.builder(C.STR_FUN_ROUTE_PAYLOAD)
            .addKdoc(C.STR_KDOC_ROUTE_PAYLOAD, originalClassName)
            .addParameter(C.STR_PARAM_ENVELOPE, originalClassName)
            .returns(rawJsonType)

        if (envelope.isGenericMode && envelope.payloadMappings.isEmpty()) {
            builder.addStatement("return envelope.%L", envelope.genericDataKotlinName!!)
        } else {
            builder.addCode(buildRouteWhenBlock(typed = false))
        }
        return builder.build()
    }

    private fun buildParsePayloadFunction(): FunSpec {
        return FunSpec.builder(C.STR_FUN_PARSE_PAYLOAD)
            .addKdoc(C.STR_KDOC_PARSE_PAYLOAD, originalClassName)
            .addParameter(C.STR_PARAM_BYTES, ByteArray::class)
            .returns(rawJsonType)
            .addCode(
                CodeBlock.builder()
                    .addStatement(
                        "val envelope = deserialize(%T(%L))",
                        flatReaderClass,
                        C.STR_PARAM_BYTES
                    )
                    .addStatement("return %L(envelope)", C.STR_FUN_ROUTE_PAYLOAD)
                    .build()
            )
            .build()
    }

    private fun buildRouteTypedFunction(): FunSpec {
        val builder = FunSpec.builder(C.STR_FUN_ROUTE_TYPED)
            .addKdoc(C.STR_KDOC_ROUTE_TYPED, originalClassName)
            .addParameter(C.STR_PARAM_ENVELOPE, originalClassName)
            .returns(nullableAnyType)

        if (envelope.isGenericMode && typedMappings.isEmpty()) {
            builder.addStatement("return envelope.%L", envelope.genericDataKotlinName!!)
        } else {
            builder.addCode(buildRouteWhenBlock(typed = true))
        }
        return builder.build()
    }

    private fun buildParseTypedFunction(): FunSpec {
        return FunSpec.builder(C.STR_FUN_PARSE_TYPED)
            .addKdoc(C.STR_KDOC_PARSE_TYPED, originalClassName)
            .addParameter(C.STR_PARAM_BYTES, ByteArray::class)
            .returns(nullableAnyType)
            .addCode(
                CodeBlock.builder()
                    .addStatement(
                        "val envelope = deserialize(%T(%L))",
                        flatReaderClass,
                        C.STR_PARAM_BYTES
                    )
                    .addStatement("return %L(envelope)", C.STR_FUN_ROUTE_TYPED)
                    .build()
            )
            .build()
    }

    private fun buildRouteWhenBlock(typed: Boolean): CodeBlock {
        val builder = CodeBlock.builder()
            .add("return when (envelope.%L) {\n", envelope.discriminatorKotlinName)

        if (envelope.isGenericMode) {
            val dataField = envelope.genericDataKotlinName!!
            envelope.payloadMappings.forEach { mapping ->
                builder.add(
                    "%S -> %L\n",
                    mapping.discriminatorValue,
                    payloadExpression(mapping, dataField, typed)
                )
            }
            builder.add("else -> envelope.%L\n", dataField)
        } else {
            envelope.payloadMappings.forEach { mapping ->
                builder.add(
                    "%S -> %L\n",
                    mapping.discriminatorValue,
                    payloadExpression(mapping, mapping.kotlinName, typed)
                )
            }
            val fallback = envelope.fallbackMapping
            if (fallback != null) {
                builder.add("else -> envelope.%L\n", fallback.kotlinName)
            } else {
                builder.add("else -> null\n")
            }
        }

        builder.add("}")
        return builder.build()
    }

    private fun payloadExpression(
        mapping: EnvelopePayloadMapping,
        fieldName: String,
        typed: Boolean
    ): CodeBlock {
        if (!typed || mapping.targetType == null) {
            return CodeBlock.of("envelope.%L", fieldName)
        }
        return if (mapping.isRawJson) {
            CodeBlock.of(
                C.TEMPLATE_ENVELOPE_TYPED_SERIALIZER,
                fieldName,
                ClassName(C.PKG_TYPES, C.STR_RAW_JSON_DECODE),
                targetSerializerPropertyName(mapping)
            )
        } else {
            CodeBlock.of("envelope.%L", fieldName)
        }
    }

    private fun targetSerializerPropertyName(mapping: EnvelopePayloadMapping): String =
        "${mapping.kotlinName}TargetSerializer"
}
