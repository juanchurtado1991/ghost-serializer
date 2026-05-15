package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.KModifier
import com.ghost.serialization.compiler.GhostEmitterConstants as C
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.TypeSpec

/**
 * Base class for all deserialization emitters.
 *
 * Provides shared state and common helper methods for generating code related to
 * reading types from GhostJsonReader and building calls to other serializers.
 */
internal abstract class BaseDeserializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val readerClass: ClassName
) {
    protected val contextualSerializers = mutableMapOf<KSType, String>()

    protected fun buildCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.customDecoder != null) return buildCustomDecoderCall(prop)
        if (prop.isNullable) return buildNullableCall(prop)

        return when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                buildCall(prop.valueClassProperty)
            }

            prop.isSealedClass -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                serializerName(prop.type)
            )

            prop.isPrimitiveArray -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}${C.STR_SERIALIZER}"
                )
            )

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
            }

            else -> buildTypeReaderCall(prop.type)
        }
    }

    protected fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.customDecoder != null) return nullGuarded(buildCustomDecoderCall(prop))
        return when {
            prop.isGhost || prop.isEnum -> CodeBlock.of(
                C.STR_NULL_CHECK_1 + C.STR_NULL_CHECK_2,
                serializerName(prop.type)
            )

            prop.type.isPrimitiveInt() -> CodeBlock.of(C.STR_NULL_CHECK_INT)
            prop.type.isPrimitiveLong() -> CodeBlock.of(C.STR_NULL_CHECK_LONG)
            prop.type.isPrimitiveDouble() -> CodeBlock.of(C.STR_NULL_CHECK_DOUBLE)
            prop.type.isPrimitiveFloat() -> CodeBlock.of(C.STR_NULL_CHECK_FLOAT)
            prop.type.isPrimitiveBoolean() -> CodeBlock.of(C.STR_NULL_CHECK_BOOLEAN)
            prop.isPrimitiveArray -> nullGuarded(
                CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    ClassName(
                        C.STR_SERIALIZERS_PKG,
                        "${prop.primitiveArrayType}${C.STR_SERIALIZER}"
                    )
                )
            )
            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                nullGuarded(CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name))
            }

            else -> nullGuarded(buildTypeReaderCall(prop.type))
        }
    }

    protected fun buildCustomDecoderCall(prop: GhostPropertyModel): CodeBlock {
        return CodeBlock.of(C.TEMPLATE_L_READER, prop.customDecoder!!)
    }

    protected fun buildTypeReaderCall(type: KSType): CodeBlock {
        return when {
            type.isGhost() || type.isEnum() -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                serializerName(type)
            )
            type.isPrimitiveInt() -> CodeBlock.of(C.STR_NEXT_INT)
            type.isPrimitiveBoolean() -> CodeBlock.of(C.STR_NEXT_BOOLEAN)
            type.isPrimitiveLong() -> CodeBlock.of(C.STR_NEXT_LONG)
            type.isPrimitiveDouble() -> CodeBlock.of(C.STR_NEXT_DOUBLE)
            type.isPrimitiveFloat() -> CodeBlock.of(C.STR_NEXT_FLOAT)
            type.isList() -> {
                val inner = type.arguments.firstOrNull()?.type?.resolve()
                    ?: return CodeBlock.of(C.STR_NEXT_STRING)

                CodeBlock.of(
                    C.STR_READ_LIST_TEMPLATE,
                    buildTypeReaderCall(inner))

            }

            type.isMap() -> {
                val valueType = type
                    .arguments
                    .getOrNull(1)
                    ?.type?.resolve()
                    ?: return CodeBlock.of(C.STR_NEXT_STRING)

                CodeBlock.of(
                    C.STR_READ_MAP_TEMPLATE,
                    buildTypeReaderCall(valueType)
                )
            }

            else -> {
                if (type.declaration.qualifiedName?.asString() == C.KOTLIN_STRING) {
                    CodeBlock.of(C.STR_NEXT_STRING)
                } else {
                    val name = getContextualSerializerName(type)
                    CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
                }
            }
        }
    }

    private fun getContextualSerializerName(type: KSType): String {
        return contextualSerializers.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            "contextual_${simpleName.replaceFirstChar { it.lowercase() }}Serializer"
        }
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
                    )
                        .parameterizedBy(type.toTypeName()),
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

    protected fun nullGuarded(inner: CodeBlock): CodeBlock =
        CodeBlock.of(C.TEMPLATE_NULL_CHECK_L, inner)

    protected fun serializerName(type: KSType): ClassName =
        with(type.declaration as KSClassDeclaration) {
            val className = toClassName()
            return ClassName(
                className.packageName,
                "${className.simpleNames.joinToString(C.STR_UNDERSCORE)}${C.STR_SERIALIZER}"
            )
        }
}
