@file:Suppress("unused", "SameParameterValue")

package com.ghost.serialization.compiler

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
 * This emitter is designed for large classes (typically > 40 properties) to bypass JVM
 * method size limits (64KB bytecode limit). It fragments the decoding process by creating
 * a helper context class (`DecodingContext`) to store deserialized properties and bitmasks,
 * and splitting the field assignment logic into multiple chunk methods (e.g. `decodeChunk0`, `decodeChunk1`).
 *
 * @property properties The list of property models.
 * @property originalClassName The target class to deserialize.
 * @property readerClass The reader implementation class used.
 */
internal class FragmentedEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    /**
     * Emits the fragmented deserialization logic.
     *
     * It delegates the construction of the private `DecodingContext` class, declarations
     * of chunk functions, and the emission of the main parsing loop to smaller helper methods,
     * then validates and instantiates the target DTO.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param typeSpecBuilder The serializer class builder.
     * @param isFlatPath Whether the flat reader path is used.
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
                readerClass,
                contextClassName,
                typeSpecBuilder
            )
        }

        emitMainParseLoop(body, chunks, chunkSize)
        emitValidation(body)
        emitReturn(body, typeSpecBuilder)

        emitValidationHelper(typeSpecBuilder, contextClassName)
    }

    /**
     * Builds and registers the private `DecodingContext` class to track properties and masks.
     *
     * @param typeSpecBuilder The serializer class builder.
     * @param contextClassName Class name of the context object holding property variables.
     * @param isFlatPath Whether the flat reader path is used.
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
                    it.kotlinName,
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
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param chunks Grouped DTO properties.
     * @param chunkSize Size of a chunk.
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
        body.addStatement(C.STR_END_OBJECT)
    }

    /**
     * Emits a private chunk decoding helper function.
     *
     * This generated method maps index selections directly to field assignments and tracking masks
     * in the `DecodingContext` instance, keeping the size of each method small.
     *
     * @param chunkIdx The chunk index.
     * @param chunkProps The list of DTO properties assigned to this chunk.
     * @param chunkSize Size of a chunk.
     * @param readerClass The reader class used.
     * @param contextClassName Class name of the context object holding property variables.
     * @param typeSpecBuilder Serializer class builder.
     */
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
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_READER_VAR, readerClass)
            .addParameter(C.STR_CTX_VAR, contextClassName)
            .addParameter(C.STR_INDEX_VAR, INT)

        val chunkBody = CodeBlock.builder()
        chunkBody.beginControlFlow(C.STR_WHEN_INDEX_PLAIN)
        chunkProps.forEachIndexed { innerIdx, prop ->
            val globalIndex = chunkIdx * chunkSize + innerIdx
            val call = buildCall(prop)
            val maskIdx = globalIndex / C.MASK_SIZE_BITS.toInt()
            val constName = "MASK_" + prop.kotlinName.uppercase()
            
            chunkBody.beginControlFlow("$globalIndex${C.STR_ARROW}")
            if (prop.isResilient) {
                chunkBody.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_SET_IT, prop.kotlinName)
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, constName)
                chunkBody.endControlFlow()
            } else {
                chunkBody.addStatement(C.TEMPLATE_CTX_FIELD_ASSIGN, prop.kotlinName, call)
                chunkBody.addStatement(C.TEMPLATE_CTX_MASK_OR, maskIdx, maskIdx, constName)
            }
            chunkBody.endControlFlow()
        }
        chunkBody.endControlFlow()
        chunkFun.addCode(chunkBody.build())
        typeSpecBuilder.addFunction(chunkFun.build())
    }

    /**
     * Emits required properties validation logic.
     *
     * Iterates over bitmasks. If a mask has required fields, it emits validation code that
     * checks the tracking mask in `DecodingContext`.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     */
    private fun emitValidation(body: CodeBlock.Builder) {
        val hasRequired = properties.any { !it.isNullable && !it.hasDefaultValue }
        if (hasRequired) {
            body.addStatement(C.TEMPLATE_CALL_VALIDATION, C.STR_FUN_VALIDATE_FIELDS, C.STR_CTX_VAR, C.STR_READER_VAR)
        }
    }

    /**
     * Generates a descriptive private helper method validating that all required properties
     * were present in the bitmask of the context class, throwing a GhostJsonException for any missing field.
     *
     * @param typeSpecBuilder The serializer class builder.
     * @param contextClassName Class name of the context object holding property variables.
     */
    private fun emitValidationHelper(typeSpecBuilder: TypeSpec.Builder, contextClassName: ClassName) {
        val hasRequired = properties.any { !it.isNullable && !it.hasDefaultValue }
        if (!hasRequired) {
            return
        }

        val funBuilder = FunSpec.builder(C.STR_FUN_VALIDATE_FIELDS)
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_CTX_VAR, contextClassName)
            .addParameter(C.STR_READER_VAR, readerClass)

        val funBody = CodeBlock.builder()
        for (maskIdx in C.VAL_ZERO until maskCount) {
            val reqMask = requiredMasks[maskIdx]
            if (reqMask != C.VAL_ZERO_L) {
                val requiredMaskName = "MASK_REQUIRED_$maskIdx"

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
                        val constName = "MASK_" + prop.kotlinName.uppercase()

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
                            C.TEMPLATE_THROW_S,
                            C.STR_REQ_FIELD_1 + prop.jsonName + C.STR_REQ_FIELD_2
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
     * Emits the target class instantiation return statement.
     *
     * Resolves variables from `DecodingContext`. Uses copy-based updates for default properties.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitReturn(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { it.isInConstructor && !it.hasDefaultValue }
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)

        requiredProps.forEach { prop ->
            body.addStatement(
                C.TEMPLATE_NAMED_ARG,
                prop.kotlinName,
                prop.getFragmentedReturnExpression()
            )
        }

        body.addStatement(C.STR_PAREN)

        val defaultProps = properties.filter { it.isInConstructor && it.hasDefaultValue }
        if (defaultProps.isNotEmpty()) {
            body.add(C.STR_IF_OPEN)

            val conditions = mutableListOf<String>()
            for (i in defaultMasks.indices) {
                val defMask = defaultMasks[i]
                if (defMask != C.VAL_ZERO_L) {
                    val constName = "MASK_DEFAULTS_$i"

                    conditions.add(
                        C.TEMPLATE_IF_MASK_MATCH_BIT_F
                            .format(i, constName)
                    )
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            val defaultPropsWithGlobalIndex = properties
                .mapIndexedNotNull { globalIdx, prop ->

                if (prop.isInConstructor && prop.hasDefaultValue) {
                    Pair(globalIdx, prop)
                } else {
                    null
                }
            }

            defaultPropsWithGlobalIndex.forEachIndexed { _, (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val constName = "MASK_" + prop.kotlinName.uppercase()

                val valueExpr = prop
                    .getFragmentedDefaultValueReturnExpression(maskIdx, constName)
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
