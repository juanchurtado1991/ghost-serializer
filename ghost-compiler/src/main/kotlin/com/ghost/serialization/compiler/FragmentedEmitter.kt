@file:Suppress("unused", "SameParameterValue")

package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Emitter for fragmented deserialization logic.
 *
 * Used for large classes (typically > 40 properties) to avoid method size limits
 * by splitting the decoding logic into multiple "chunks" and using a helper
 * context class to maintain state across method calls.
 */
internal class FragmentedEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    fun emit(
        body: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        val contextClassName = ClassName(
            C.STR_EMPTY,
            C.STR_CTX_CLASS
        )

        val contextBuilder = TypeSpec.classBuilder(contextClassName)
            .addModifiers(KModifier.PRIVATE)

        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            contextBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_UNDERSCORE + it.kotlinName,
                    varType
                )
                    .mutable(true)
                    .initializer(initialValue)
                    .build()
            )
        }

        val maskCount = (properties.size + C.MASK_SIZE_BITS_MINUS_ONE) /
                C.MASK_SIZE_BITS.toInt()

        for (index in 0 until maskCount) {
            contextBuilder.addProperty(
                PropertySpec.builder(
                    C.FMT_MASK_NAME.format(
                        C.STR_UNDERSCORE,
                        C.STR_MASK,
                        index
                    ),
                    com.squareup.kotlinpoet.LONG
                )
                    .mutable(true)
                    .initializer(C.STR_ZERO_L)
                    .build()
            )
        }

        typeSpecBuilder.addType(contextBuilder.build())

        val chunkSize = C.DEFAULT_CHUNK_SIZE
        val chunks = properties.chunked(chunkSize)
        chunks.forEachIndexed { chunkIdx, chunkProps ->
            emitChunkFunction(
                chunkIdx,
                chunkProps,
                chunkSize,
                readerClass,
                contextClassName,
                typeSpecBuilder
            )
        }

        body.addStatement(C.STR_CTX_INIT)
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)
        
        chunks.forEachIndexed { chunkIdx, chunkProps ->
            val start = chunkIdx * chunkSize
            val end = start + chunkProps.size - 1
            val chunkFunName = C.TEMPLATE_DECODE_CHUNK_NAME
                .format(C.STR_DECODE_CHUNK_PREFIX, chunkIdx)

            body.addStatement(
                C.TEMPLATE_CHUNK_CALL,
                start,
                end,
                chunkFunName
            )
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)

        body.beginControlFlow(C.STR_MINUS_TWO_ARROW)
        body.addStatement(C.STR_SKIP_VALUE)
        body.endControlFlow()
        body.endControlFlow() // when
        body.endControlFlow() // while
        body.addStatement(C.STR_END_OBJECT)

        emitValidation(body)
        emitReturn(body)
    }

    private fun emitChunkFunction(
        chunkIdx: Int,
        chunkProps: List<GhostPropertyModel>,
        chunkSize: Int,
        readerClass: ClassName,
        contextClassName: ClassName,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        val chunkFun = FunSpec
            .builder(
                C.TEMPLATE_DECODE_CHUNK_NAME
                    .format(C.STR_DECODE_CHUNK_PREFIX, chunkIdx)
            )
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember(C.MARKER, C.STR_NOTHING_TO_INLINE)
                    .build()
            )
            .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
            .addParameter(C.STR_READER_VAR, readerClass)
            .addParameter(C.STR_CTX_VAR, contextClassName)
            .addParameter(C.STR_INDEX_VAR, INT)

        val chunkBody = CodeBlock.builder()
        chunkBody.beginControlFlow(C.STR_WHEN_INDEX_PLAIN)
        chunkProps.forEachIndexed { innerIdx, prop ->
            val globalIndex = chunkIdx * chunkSize + innerIdx
            val call = buildCall(prop)
            val maskIdx = globalIndex / 64
            val bitIdx = globalIndex % C.MASK_SIZE_BITS.toInt()
            val bitMask = 1L shl bitIdx
            val bitMaskStr = if (bitMask == Long.MIN_VALUE) {
                C.STR_BIT_MASK_MIN_LONG
            } else {
                C.FMT_LONG_LITERAL.format(bitMask)
            }
            
            chunkBody.beginControlFlow("$globalIndex${C.STR_ARROW}")
            if (prop.isResilient) {
                chunkBody.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_SET_IT, prop.kotlinName)
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, bitMaskStr)
                chunkBody.endControlFlow()
            } else {
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_ASSIGN, prop.kotlinName, call)
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, bitMaskStr)
            }
            chunkBody.endControlFlow()
        }
        chunkBody.endControlFlow()
        chunkFun.addCode(chunkBody.build())
        typeSpecBuilder.addFunction(chunkFun.build())
    }

    private fun emitValidation(body: CodeBlock.Builder) {
        val maskCount = (properties.size + 63) / 64
        val requiredMasks = LongArray(maskCount)
        properties.forEachIndexed { index, it ->
            if (!it.isNullable && !it.hasDefaultValue) {
                val maskIdx = index / 64
                val bitIdx = index % 64
                requiredMasks[maskIdx] = requiredMasks[maskIdx] or (1L shl bitIdx)
            }
        }

        for (index in 0 until maskCount) {
            val reqMask = requiredMasks[index]
            if (reqMask != 0L) {

                val reqMaskStr = if (reqMask == Long.MIN_VALUE) {
                    C.STR_BIT_MASK_MIN_LONG
                } else {
                    C.FMT_LONG_LITERAL.format(reqMask)
                }

                body.beginControlFlow(
                    C.TEMPLATE_IF_MASK_NOT_MET,
                    index,
                    reqMaskStr,
                    reqMaskStr
                )

                properties.forEachIndexed { index, it ->
                    if (
                        !it.isNullable &&
                        !it.hasDefaultValue
                        && (index / C.MASK_SIZE_BITS.toInt()) == index
                    ) {
                        val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                        val bitMask = 1L shl bitIdx

                        val bitMaskStr = if (bitMask == Long.MIN_VALUE) {
                            C.STR_BIT_MASK_MIN_LONG
                        } else {
                            C.FMT_LONG_LITERAL.format(bitMask)
                        }

                        body.beginControlFlow(
                            C.TEMPLATE_IF_MASK_MISSING,
                            index,
                            bitMaskStr
                        )

                        body.addStatement(
                            C.TEMPLATE_THROW_S,
                            C.STR_REQ_FIELD_1 + it.jsonName + C.STR_REQ_FIELD_2
                        )

                        body.endControlFlow()
                    }
                }
                body.endControlFlow()
            }
        }
    }

    private fun emitReturn(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)

        requiredProps.forEach { prop ->
            body.addStatement(
                C.TEMPLATE_NAMED_ARG,
                prop.kotlinName,
                prop.getFragmentedReturnExpression()
            )
        }

        body.addStatement(C.STR_PAREN)

        val defaultProps = properties.filter { it.hasDefaultValue }
        if (defaultProps.isNotEmpty()) {
            body.add(C.STR_IF_OPEN)
            val maskCount = (properties.size + 63) / 64
            val defaultMasks = LongArray(maskCount)
            properties.forEachIndexed { index, it ->
                if (it.hasDefaultValue) {
                    val maskIdx = index / 64
                    val bitIdx = index % 64
                    defaultMasks[maskIdx] = defaultMasks[maskIdx] or (1L shl bitIdx)
                }
            }

            val conditions = mutableListOf<String>()
            for (i in defaultMasks.indices) {
                val defMask = defaultMasks[i]
                if (defMask != 0L) {
                    val defMaskStr = if (defMask == Long.MIN_VALUE) {
                        C.STR_BIT_MASK_MIN_LONG
                    } else {
                        C.FMT_LONG_LITERAL.format(defMask)
                    }

                    conditions.add(
                        C.TEMPLATE_IF_MASK_MATCH_BIT_F
                            .format(i, defMaskStr)
                    )
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            val defaultPropsWithGlobalIndex = properties
                .mapIndexedNotNull { globalIdx, prop ->

                if (prop.hasDefaultValue) {
                    Pair(globalIdx, prop)
                } else {
                    null
                }
            }

            defaultPropsWithGlobalIndex.forEachIndexed { _, (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val bitIdx = propIndex % C.MASK_SIZE_BITS.toInt()
                val bitMask = 1L shl bitIdx

                val bitMaskStr = if (bitMask == Long.MIN_VALUE) {
                    C.STR_BIT_MASK_MIN_LONG
                } else {
                    C.FMT_LONG_LITERAL.format(bitMask)
                }

                val valueExpr = prop
                    .getFragmentedDefaultValueReturnExpression(maskIdx, bitMaskStr)
                body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, valueExpr)
            }
            body.addStatement(C.STR_PAREN)
            body.nextControlFlow(C.STR_ELSE)
            body.addStatement(C.STR_RETURN_RESULT_FINAL)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_RETURN_RESULT_FINAL)
        }
    }
}
