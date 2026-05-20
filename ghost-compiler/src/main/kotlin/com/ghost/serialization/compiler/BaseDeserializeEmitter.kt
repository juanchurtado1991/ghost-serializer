package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

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

    protected val fullPaths = properties.map {
        it.flattenPath ?: (it.wrapPath?.let { path ->
            path + it.jsonName
        } ?: listOf(it.jsonName))
    }

    protected val propertyIndices = properties.mapIndexed { index, prop -> prop to index }.toMap()

    protected val maskCount = (properties.size + C.MASK_SIZE_BITS_MINUS_ONE) / C.MASK_SIZE_BITS.toInt()

    protected val requiredMasks: LongArray by lazy {
        val masks = LongArray(maskCount)
        properties.forEachIndexed { index, prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val maskIdx = index / C.MASK_SIZE_BITS.toInt()
                val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                masks[maskIdx] = masks[maskIdx] or (1L shl bitIdx)
            }
        }
        masks
    }

    protected val defaultMasks: LongArray by lazy {
        val masks = LongArray(maskCount)
        properties.forEachIndexed { index, prop ->
            if (prop.hasDefaultValue) {
                val maskIdx = index / C.MASK_SIZE_BITS.toInt()
                val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                masks[maskIdx] = masks[maskIdx] or (1L shl bitIdx)
            }
        }
        masks
    }

    protected fun formatMaskString(mask: Long): String {
        return if (mask == Long.MIN_VALUE) {
            C.STR_BIT_MASK_MIN_LONG
        } else {
            C.FMT_LONG_LITERAL.format(mask)
        }
    }

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
                prop.type.serializerClassName()
            )

            prop.isPrimitiveArray -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
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
        if (prop.customDecoder != null) {
            return nullGuarded(buildCustomDecoderCall(prop))
        }

        if (prop.isPrimitiveArray) {
            return nullGuarded(
                CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    ClassName(
                        C.STR_SERIALIZERS_PKG,
                        "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
                    )
                )
            )
        }

        return buildTypeReaderCall(prop.type)
    }

    protected fun buildCustomDecoderCall(prop: GhostPropertyModel): CodeBlock {
        val coder = prop.customDecoder!!
        if (readerClass.simpleName == C.STR_GHOST_JSON_FLAT_READER) {
            return CodeBlock.builder()
                .add(C.STR_RUN_OPEN)
                .add(C.STR_CUSTOM_DECODER_TEMP_READER)
                .add(C.TEMPLATE_CUSTOM_DECODER_TEMP_CALL, coder.provider, coder.functionName)
                .add(C.STR_CUSTOM_DECODER_UPDATE_POS)
                .add(C.STR_RESET_TOKEN_BYTE_CALL)
                .add(C.STR_CUSTOM_DECODER_RETURN_RES)
                .add(C.STR_RUN_CLOSE)
                .build()
        }
        return CodeBlock.of(C.TEMPLATE_L_READER, coder.provider, coder.functionName)
    }

    protected fun buildTypeReaderCall(type: KSType): CodeBlock {
        val readerCall = when {
            type.isGhost() || type.isEnum() -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                type.serializerClassName()
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
                    buildTypeReaderCall(inner)
                )

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
                if (type.isString()) {
                    CodeBlock.of(C.STR_NEXT_STRING)
                } else {
                    val name = getContextualSerializerName(type)
                    CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
                }
            }
        }

        return if (type.isMarkedNullable) {
            nullGuarded(readerCall)
        } else {
            readerCall
        }
    }

    private fun getContextualSerializerName(type: KSType): String {
        return contextualSerializers.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            C.STR_CONTEXTUAL_PREFIX +
                    simpleName.replaceFirstChar { it.lowercase() } +
                    C.STR_SERIALIZER_SUFFIX
        }
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
}
