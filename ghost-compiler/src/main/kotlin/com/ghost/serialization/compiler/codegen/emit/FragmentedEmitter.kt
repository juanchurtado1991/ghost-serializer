package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.analysis.allDefaultsHaveExpressions
import com.ghost.serialization.compiler.analysis.getFragmentedDefaultValueReturnExpression
import com.ghost.serialization.compiler.analysis.getFragmentedReturnExpression
import com.ghost.serialization.compiler.analysis.getFragmentedSingleShotDefaultArgExpression
import com.ghost.serialization.compiler.analysis.getInitialValue
import com.ghost.serialization.compiler.analysis.getVariableType
import com.ghost.serialization.compiler.analysis.localTrackingName
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Emitter for fragmented deserialization logic, used for large classes (> `PROPERTY_MAX_SIZE`
 * properties) to avoid JVM method size limits. Fragments decoding across a `DecodingContext`
 * class and per-chunk methods (`decodeChunk0`, `decodeChunk1`, ...).
 */
internal class FragmentedEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName,
    private val supportsResilience: Boolean = true,
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    /**
     * Emits the fragmented deserialization logic: builds the `DecodingContext`, chunk
     * functions, and the main parsing loop, then validates and instantiates the target DTO.
     */
    fun emit(
        body: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder,
        isFlatPath: Boolean = false
    ) {
        emitPropertyMaskConstants(typeSpecBuilder)
        val contextClassName = ClassName(
            C.STR_EMPTY,
            C.STR_CTX_CLASS
        )
        val chunkSize = C.DEFAULT_CHUNK_SIZE
        val chunks = properties.chunked(chunkSize)

        buildDecodingContext(typeSpecBuilder, contextClassName, isFlatPath)

        chunks.forEachIndexed { chunkIdx, chunkProps ->
            emitChunkFunction(
                chunkIdx,
                chunkProps,
                chunkSize,
                contextClassName,
                typeSpecBuilder
            )
        }

        emitMainParseLoop(body, chunks, chunkSize)
        // Validate before endObject so JSONPath still includes the current object frame.
        emitValidation(body)
        body.addStatement(C.STR_END_OBJECT)
        emitReturn(body, typeSpecBuilder)

        emitValidationHelper(typeSpecBuilder, contextClassName)
    }

    /**
     * Builds and registers the private `DecodingContext` class to track properties and masks.
     */
    private fun buildDecodingContext(
        typeSpecBuilder: TypeSpec.Builder,
        contextClassName: ClassName,
        isFlatPath: Boolean
    ) {
        val contextBuilder = TypeSpec.classBuilder(contextClassName)
            .addModifiers(KModifier.PRIVATE)

        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            contextBuilder.addProperty(
                PropertySpec.builder(
                    it.localTrackingName(),
                    varType
                )
                    .mutable(true)
                    .initializer(initialValue)
                    .build()
            )
        }

        for (index in C.VAL_ZERO until maskCount) {
            contextBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_MASK_INDEX_FMT.format(index),
                    com.squareup.kotlinpoet.LONG
                )
                    .mutable(true)
                    .initializer(C.STR_ZERO_L)
                    .build()
            )
        }

        if (!isFlatPath) {
            typeSpecBuilder.addType(contextBuilder.build())
        }
    }

    /**
     * Emits the main parse loop mapping selector indexes to fragmented chunk calls.
     */
    private fun emitMainParseLoop(
        body: CodeBlock.Builder,
        chunks: List<List<GhostPropertyModel>>,
        chunkSize: Int
    ) {
        body.addStatement(C.STR_CTX_INIT)
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        chunks.forEachIndexed { chunkIdx, chunkProps ->
            val start = chunkIdx * chunkSize
            val end = start + chunkProps.size - C.VAL_ONE
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
        // endObject emitted after validation (see emit)
    }

    /**
     * Emits a private chunk decoding helper that maps index selections to field assignments
     * and tracking masks in `DecodingContext`, keeping each generated method small.
     */
    private fun emitChunkFunction(
        chunkIdx: Int,
        chunkProps: List<GhostPropertyModel>,
        chunkSize: Int,
        contextClassName: ClassName,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        val chunkFun = FunSpec
            .builder(
                C.TEMPLATE_DECODE_CHUNK_NAME
                    .format(C.STR_DECODE_CHUNK_PREFIX, chunkIdx)
            )
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_READER, readerClass)
            .addParameter(C.STR_CTX_VAR, contextClassName)
            .addParameter(C.STR_INDEX_VAR, INT)

        val chunkBody = CodeBlock.builder()
        chunkBody.beginControlFlow(C.STR_WHEN_INDEX_PLAIN)
        chunkProps.forEachIndexed { innerIdx, prop ->
            val globalIndex = chunkIdx * chunkSize + innerIdx
            val call = buildCall(prop)
            val maskIdx = globalIndex / C.MASK_SIZE_BITS.toInt()
            val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()

            chunkBody.beginControlFlow("$globalIndex${C.STR_ARROW}")
            if (prop.isResilient && supportsResilience) {
                chunkBody.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_SET_IT, prop.localTrackingName())
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, constName)
                chunkBody.endControlFlow()
            } else {
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_ASSIGN, prop.localTrackingName(), call)
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, constName)
            }
            chunkBody.endControlFlow()
        }
        chunkBody.endControlFlow()
        chunkFun.addCode(chunkBody.build())
        typeSpecBuilder.addFunction(chunkFun.build())
    }

    /**
     * Emits a call to validate required properties against the tracking masks in `DecodingContext`.
     */
    private fun emitValidation(body: CodeBlock.Builder) {
        val hasRequired = properties.any { !it.isNullable && !it.hasDefaultValue }
        if (hasRequired) {
            body.addStatement(
                C.TEMPLATE_CALL_VALIDATION,
                C.STR_FUN_VALIDATE_FIELDS,
                C.STR_CTX_VAR,
                C.STR_READER
            )
        }
    }

    /**
     * Generates a private helper validating that all required properties were present in the
     * bitmask, throwing a GhostJsonException for a missing field.
     */
    private fun emitValidationHelper(
        typeSpecBuilder: TypeSpec.Builder,
        contextClassName: ClassName
    ) {
        val hasRequired = properties.any { !it.isNullable && !it.hasDefaultValue }
        if (!hasRequired) {
            return
        }

        val funBuilder = FunSpec.builder(C.STR_FUN_VALIDATE_FIELDS)
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_CTX_VAR, contextClassName)
            .addParameter(C.STR_READER, readerClass)

        val funBody = CodeBlock.builder()
        for (maskIdx in C.VAL_ZERO until maskCount) {
            val reqMask = requiredMasks[maskIdx]
            if (reqMask != C.VAL_ZERO_L) {
                val requiredMaskName = C.STR_MASK_REQUIRED_PREFIX + maskIdx

                funBody.beginControlFlow(
                    C.TEMPLATE_IF_MASK_NOT_MET,
                    maskIdx,
                    requiredMaskName,
                    requiredMaskName
                )

                var isFirst = true
                properties.forEachIndexed { propIdx, prop ->
                    if (
                        !prop.isNullable &&
                        !prop.hasDefaultValue
                        && (propIdx / C.MASK_SIZE_BITS.toInt()) == maskIdx
                    ) {
                        val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()

                        if (isFirst) {
                            funBody.beginControlFlow(
                                C.TEMPLATE_IF_MASK_MISSING,
                                maskIdx,
                                constName
                            )
                            isFirst = false
                        } else {
                            funBody.nextControlFlow(
                                C.TEMPLATE_ELSE_IF_MASK_MISSING,
                                maskIdx,
                                constName
                            )
                        }

                        funBody.addStatement(
                            C.TEMPLATE_THROW_MISSING_REQUIRED,
                            prop.jsonName
                        )
                    }
                }
                if (!isFirst) {
                    funBody.endControlFlow()
                }
                funBody.endControlFlow()
            }
        }

        funBuilder.addCode(funBody.build())
        typeSpecBuilder.addFunction(funBuilder.build())
    }

    /**
     * Emits the target class instantiation return statement, resolving variables from
     * `DecodingContext` and using copy-based updates for default properties.
     */
    private fun emitReturn(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { it.isInConstructor && !it.hasDefaultValue }
        val defaultPropsWithGlobalIndex = properties.mapIndexedNotNull { globalIdx, prop ->
            if (prop.isInConstructor && prop.hasDefaultValue) {
                Pair(globalIdx, prop)
            } else {
                null
            }
        }

        if (defaultPropsWithGlobalIndex.map { it.second }.allDefaultsHaveExpressions()) {
            body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
            requiredProps.forEach { prop ->
                body.addStatement(
                    C.TEMPLATE_NAMED_ARG,
                    prop.kotlinName,
                    prop.getFragmentedReturnExpression()
                )
            }
            defaultPropsWithGlobalIndex.forEach { (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
                body.addStatement(
                    C.TEMPLATE_NAMED_ARG,
                    prop.kotlinName,
                    prop.getFragmentedSingleShotDefaultArgExpression(maskIdx, constName)
                )
            }
            body.addStatement(C.STR_PAREN)
            body.addStatement(C.STR_RETURN_RESULT)
            return
        }

        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
        requiredProps.forEach { prop ->
            body.addStatement(
                C.TEMPLATE_NAMED_ARG,
                prop.kotlinName,
                prop.getFragmentedReturnExpression()
            )
        }
        body.addStatement(C.STR_PAREN)

        if (defaultPropsWithGlobalIndex.isNotEmpty()) {
            body.add(C.STR_IF_OPEN)
            val conditions = mutableListOf<String>()
            for (i in defaultMasks.indices) {
                val defMask = defaultMasks[i]
                if (defMask != C.VAL_ZERO_L) {
                    val constName = emitDefaultMaskConstant(typeSpecBuilder, i)
                    conditions.add(
                        C.TEMPLATE_IF_MASK_MATCH_BIT_F
                            .format(i, constName)
                    )
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            defaultPropsWithGlobalIndex.forEach { (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
                val valueExpr = prop.getFragmentedDefaultValueReturnExpression(maskIdx, constName)
                body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, valueExpr)
            }
            body.addStatement(C.STR_PAREN)
            body.nextControlFlow(C.STR_ELSE)
            body.addStatement(C.STR_RETURN_RESULT)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_RETURN_RESULT)
        }
    }
}
