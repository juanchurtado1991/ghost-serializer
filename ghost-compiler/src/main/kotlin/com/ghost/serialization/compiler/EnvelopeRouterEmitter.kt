package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

private val nullableAnyType = ClassName(C.PKG_KOTLIN, "Any").copy(nullable = true)

/**
 * Emits zero-copy envelope routing helpers on generated serializer companions.
 */
internal class EnvelopeRouterEmitter(
    private val envelope: GhostEnvelopeModel,
    private val originalClassName: ClassName,
    private val flatReaderClass: ClassName
) {

    fun emit(typeSpecBuilder: TypeSpec.Builder) {
        typeSpecBuilder.addFunction(buildRoutePayloadFunction())
        typeSpecBuilder.addFunction(buildParsePayloadFunction())

        if (envelope.payloadMappings.any { it.targetType != null }) {
            typeSpecBuilder.addFunction(buildRouteTypedFunction())
            typeSpecBuilder.addFunction(buildParseTypedFunction())
        }
    }

    private fun buildRoutePayloadFunction(): FunSpec {
        val code = buildRouteWhenBlock(typed = false)
        return FunSpec.builder(C.STR_FUN_ROUTE_PAYLOAD)
            .addKdoc(C.STR_KDOC_ROUTE_PAYLOAD, originalClassName)
            .addParameter(C.STR_PARAM_ENVELOPE, originalClassName)
            .returns(ClassName(C.PKG_TYPES, C.STR_RAW_JSON_TYPE).copy(nullable = true))
            .addCode(code)
            .build()
    }

    private fun buildParsePayloadFunction(): FunSpec {
        return FunSpec.builder(C.STR_FUN_PARSE_PAYLOAD)
            .addKdoc(C.STR_KDOC_PARSE_PAYLOAD, originalClassName)
            .addParameter(C.STR_PARAM_BYTES, ByteArray::class)
            .returns(ClassName(C.PKG_TYPES, C.STR_RAW_JSON_TYPE).copy(nullable = true))
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
        return FunSpec.builder(C.STR_FUN_ROUTE_TYPED)
            .addKdoc(C.STR_KDOC_ROUTE_TYPED, originalClassName)
            .addParameter(C.STR_PARAM_ENVELOPE, originalClassName)
            .returns(nullableAnyType)
            .addCode(buildRouteWhenBlock(typed = true))
            .build()
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
            if (envelope.payloadMappings.isEmpty()) {
                builder.add("else -> envelope.%L\n", dataField)
            } else {
                envelope.payloadMappings.forEach { mapping ->
                    builder.add(
                        "%S -> %L\n",
                        mapping.discriminatorValue,
                        payloadExpression(mapping, dataField, typed)
                    )
                }
                builder.add("else -> envelope.%L\n", dataField)
            }
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
                C.TEMPLATE_ENVELOPE_TYPED_RAWJSON,
                fieldName,
                ClassName(C.PKG_TYPES, C.STR_RAW_JSON_DECODE),
                mapping.targetType
            )
        } else {
            CodeBlock.of("envelope.%L", fieldName)
        }
    }
}
