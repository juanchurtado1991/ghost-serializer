@file:Suppress("unused")

package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Emitter for standard deserialization logic.
 *
 * Handles the generation of the parse loop, field validation, and the final
 * return statement for most classes (those with fewer than 40 properties).
 */
internal class StandardEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    fun emit(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            body.addStatement(
                "${C.STR_VAR_UNDERSCORE}${it.kotlinName}${C.TEMPLATE_VAR_INIT}",
                varType,
                initialValue
            )
        }

        val maskCount = (properties.size + 63) / 64
        for (i in 0 until maskCount) {
            body.addStatement(C.STR_MASK_INIT, i)
        }

        emitParseLoop(body)
        emitFieldValidation(body, typeSpecBuilder)
        emitReturnStatement(body, typeSpecBuilder)
    }

    private fun emitParseLoop(body: CodeBlock.Builder) {
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        val topLevelNames = fullPaths.map { it.first() }.distinct()

        topLevelNames.forEachIndexed { topIndex, topName ->
            body.beginControlFlow("$topIndex${C.STR_ARROW}")
            val propsForThisName = properties.filterIndexed { idx, _ -> fullPaths[idx].first() == topName }

            if (propsForThisName.size == 1 && fullPaths[properties.indexOf(propsForThisName[0])].size == 1) {
                emitPropertyAssignment(body, propsForThisName[0], properties.indexOf(propsForThisName[0]))
            } else {
                emitFlattenedGroup(body, topName, propsForThisName, 1, "")
            }
            body.endControlFlow()
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.beginControlFlow(C.STR_MINUS_TWO_ARROW)
        body.addStatement(C.STR_SKIP_VALUE)
        body.endControlFlow()
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement(C.STR_END_OBJECT)
    }

    private fun emitFlattenedGroup(
        body: CodeBlock.Builder,
        name: String,
        props: List<GhostPropertyModel>,
        pathIndex: Int,
        parentPrefix: String
    ) {
        val currentPrefix = if (parentPrefix.isEmpty()) name else "${parentPrefix}_$name"
        val optionsName = "OPTIONS_${currentPrefix.uppercase()}"

        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_SUB_NAME, optionsName)
        body.beginControlFlow(C.STR_WHEN_SUB_INDEX)

        val nextLevelNames = props.map {
            val path = fullPaths[properties.indexOf(it)]
            if (pathIndex < path.size) {
                path[pathIndex]
            } else {
                it.jsonName
            }
        }.distinct()

        nextLevelNames.forEachIndexed { subIndex, subName ->
            body.beginControlFlow("$subIndex${C.STR_ARROW}")
            val subProps = props.filter {
                val path = fullPaths[properties.indexOf(it)]
                val currentName = if (pathIndex < path.size) {
                    path[pathIndex]
                } else {
                    it.jsonName
                }
                currentName == subName
            }

            if (subProps.size == 1 && pathIndex == fullPaths[properties.indexOf(subProps[0])].size - 1) {
                emitPropertyAssignment(body, subProps[0], properties.indexOf(subProps[0]))
            } else {
                emitFlattenedGroup(body, subName, subProps, pathIndex + 1, currentPrefix)
            }
            body.endControlFlow()
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.beginControlFlow(C.STR_ELSE_BRANCH)
        body.addStatement(C.STR_SKIP_VALUE)
        body.endControlFlow()

        body.endControlFlow() // when
        body.endControlFlow() // while
        body.addStatement(C.STR_END_OBJECT)
    }

    private fun emitPropertyAssignment(body: CodeBlock.Builder, prop: GhostPropertyModel, index: Int) {
        val call = buildCall(prop)
        val maskIdx = index / 64
        val bitIdx = index % 64
        val bitMask = 1L shl bitIdx
        val bitMaskStr = if (bitMask == Long.MIN_VALUE) {
            C.STR_BIT_MASK_MIN_LONG
        } else {
            "${bitMask}L"
        }

        if (prop.isResilient) {
            body.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
            body.addStatement("${C.STR_UNDERSCORE}${prop.kotlinName}${C.TEMPLATE_ASSIGN_L}", "it")
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, bitMaskStr)
            body.endControlFlow()
        } else {
            body.addStatement("${C.STR_UNDERSCORE}${prop.kotlinName}${C.TEMPLATE_ASSIGN_L}", call)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, bitMaskStr)
        }
    }

    private fun emitFieldValidation(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val maskCount = (properties.size + 63) / 64
        val requiredMasks = LongArray(maskCount)
        properties.forEachIndexed { index, it ->
            if (!it.isNullable && !it.hasDefaultValue) {
                val maskIdx = index / 64
                val bitIdx = index % 64
                requiredMasks[maskIdx] = requiredMasks[maskIdx] or (1L shl bitIdx)
            }
        }

        for (i in 0 until maskCount) {
            val reqMask = requiredMasks[i]
            if (reqMask != 0L) {
                val reqMaskStr = if (reqMask == Long.MIN_VALUE) C.STR_BIT_MASK_MIN_LONG else "${reqMask}L"
                body.beginControlFlow(C.TEMPLATE_IF_MASK_NOT_MET_SIMPLE, i, reqMaskStr, reqMaskStr)
                properties.forEachIndexed { index, it ->
                    if (!it.isNullable && !it.hasDefaultValue && (index / 64) == i) {
                        val bitIdx = index % 64
                        val bitMask = 1L shl bitIdx
                        val bitMaskStr = if (bitMask == Long.MIN_VALUE) C.STR_BIT_MASK_MIN_LONG else "${bitMask}L"
                        body.beginControlFlow(C.TEMPLATE_IF_MASK_ZERO_SIMPLE, i, bitMaskStr)
                        body.addStatement(C.TEMPLATE_THROW_S, C.STR_REQ_FIELD_1 + it.jsonName + C.STR_REQ_FIELD_2)
                        body.endControlFlow()
                    }
                }
                body.endControlFlow()
            }
        }
    }

    private fun emitReturnStatement(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val hasDefaults = properties.any { it.hasDefaultValue }

        if (!hasDefaults) {
            val args = properties.joinToString(C.STR_COMMA_SPACE) { prop ->
                "`${prop.kotlinName}` = ${prop.getReturnExpression()}"
            }
            body.addStatement("${C.TEMPLATE_RETURN_T_PAREN}$args${C.STR_PAREN}", originalClassName)
            return
        }

        emitDefaultValueReturn(body, typeSpecBuilder)
    }

    private fun emitDefaultValueReturn(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        val defaultProps = properties.filter { it.hasDefaultValue }

        val requiredArgs = requiredProps.joinToString(C.STR_COMMA_SPACE) { prop ->
            "`${prop.kotlinName}`${C.STR_EQ_SPACE}" + if (prop.isNullable) {
                "${C.STR_UNDERSCORE}${prop.kotlinName}"
            } else {
                "${C.STR_UNDERSCORE}${prop.kotlinName}${C.STR_BANG_BANG}"
            }
        }

        body.addStatement("${C.TEMPLATE_VAL_RESULT}$requiredArgs${C.STR_PAREN}", originalClassName)

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
                    val defMaskStr = if (defMask == Long.MIN_VALUE) C.STR_BIT_MASK_MIN_LONG else "${defMask}L"
                    conditions.add(C.TEMPLATE_MASK_CHECK_MATCH.format(i, defMaskStr))
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            val defaultPropsWithGlobalIndex = properties.mapIndexedNotNull { globalIdx, prop ->
                if (prop.hasDefaultValue) Pair(globalIdx, prop) else null
            }
            defaultPropsWithGlobalIndex.forEachIndexed { localIdx, (propIndex, prop) ->
                val maskIdx = propIndex / 64
                val bitIdx = propIndex % 64
                val bitMask = 1L shl bitIdx
                val bitMaskStr = if (bitMask == Long.MIN_VALUE) C.STR_BIT_MASK_MIN_LONG else "${bitMask}L"
                val comma = if (localIdx < defaultPropsWithGlobalIndex.size - 1) C.STR_COMMA else C.STR_EMPTY
                val valueExpr = prop.getDefaultValueReturnExpression(maskIdx, bitMaskStr)
                body.addStatement("${C.STR_BACKTICK}%L${C.STR_BACKTICK}${C.TEMPLATE_ASSIGN_L}${C.STR_COMMA}", prop.kotlinName, valueExpr)
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
