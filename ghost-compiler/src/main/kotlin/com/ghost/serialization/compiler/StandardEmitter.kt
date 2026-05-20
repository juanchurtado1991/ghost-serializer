@file:Suppress("unused")

package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
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

    /**
     * Entry point to emit all standard deserialization code block builders.
     */
    fun emit(body: CodeBlock.Builder) {
        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            body.addStatement(
                "${
                    C.STR_VAR_UNDERSCORE
                }${
                    it.kotlinName
                }${
                    C.TEMPLATE_VAR_TYPE_INIT
                }",
                varType,
                initialValue
            )
        }

        for (i in 0 until maskCount) {
            body.addStatement(C.STR_MASK_INIT, i)
        }

        emitParseLoop(body)
        emitFieldValidation(body)
        emitReturnStatement(body)
    }

    /**
     * Generates the main field parsing loop using the perfect hash options.
     */
    private fun emitParseLoop(body: CodeBlock.Builder) {
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        val topLevelNames = fullPaths.map { it.first() }.distinct()

        topLevelNames.forEachIndexed { topIndex, topName ->
            body.beginControlFlow(
                C.TEMPLATE_WHEN_BRANCH,
                topIndex
            )

            val propsForThisName = properties
                .filterIndexed { index, _ -> fullPaths[index].first() == topName }

            if (
                propsForThisName.size == 1 &&
                fullPaths[propertyIndices[propsForThisName[0]]!!].size == 1
            ) {
                emitPropertyAssignment(
                    body,
                    propsForThisName[0],
                    propertyIndices[propsForThisName[0]]!!
                )
            } else {
                emitFlattenedGroup(
                    body,
                    topName,
                    propsForThisName,
                    1,
                    C.STR_EMPTY
                )
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

    /**
     * Recursively generates nested parsing loops for flattened structure properties.
     */
    private fun emitFlattenedGroup(
        body: CodeBlock.Builder,
        name: String,
        props: List<GhostPropertyModel>,
        pathIndex: Int,
        parentPrefix: String
    ) {
        val currentPrefix = if (parentPrefix.isEmpty()) {
            name
        } else {
            "${parentPrefix}${C.STR_UNDERSCORE}$name"
        }

        val optionsName = C.STR_OPTIONS_PREFIX + currentPrefix.uppercase()

        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_SUB_NAME, optionsName)
        body.beginControlFlow(C.STR_WHEN_SUB_INDEX)

        val nextLevelNames = props.map {
            val path = fullPaths[propertyIndices[it]!!]
            if (pathIndex < path.size) {
                path[pathIndex]
            } else {
                it.jsonName
            }
        }.distinct()

        nextLevelNames.forEachIndexed { subIndex, subName ->
            body.beginControlFlow(
                C.TEMPLATE_WHEN_BRANCH,
                subIndex
            )
            val subProps = props.filter {
                val path = fullPaths[propertyIndices[it]!!]
                val currentName = if (pathIndex < path.size) {
                    path[pathIndex]
                } else {
                    it.jsonName
                }
                currentName == subName
            }

            if (
                subProps.size == 1 &&
                pathIndex == fullPaths[propertyIndices[subProps[0]]!!].size - 1
            ) {
                emitPropertyAssignment(
                    body,
                    subProps[0],
                    propertyIndices[subProps[0]]!!
                )
            } else {
                emitFlattenedGroup(
                    body,
                    subName,
                    subProps,
                    pathIndex + 1,
                    currentPrefix
                )
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

    /**
     * Generates a single property assignment step.
     */
    private fun emitPropertyAssignment(
        body: CodeBlock.Builder,
        prop: GhostPropertyModel,
        index: Int
    ) {
        val call = buildCall(prop)
        val maskIdx = index / C.MASK_SIZE_BITS.toInt()
        val bitIdx = index % C.MASK_SIZE_BITS.toInt()
        val bitMask = 1L shl bitIdx
        val bitMaskStr = formatMaskString(bitMask)

        if (prop.isResilient) {
            body.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
            body.addStatement(C.STR_UNDERSCORE + prop.kotlinName + C.TEMPLATE_ASSIGN_L, C.STR_IT)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, bitMaskStr)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_UNDERSCORE + prop.kotlinName + C.TEMPLATE_ASSIGN_L, call)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, bitMaskStr)
        }
    }

    /**
     * Generates required field presence validation checks using bitwise operations.
     */
    private fun emitFieldValidation(body: CodeBlock.Builder) {
        val requiredPropsByMask = Array(maskCount) { mutableListOf<GhostPropertyModel>() }

        properties.forEachIndexed { index, prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val maskIdx = index / C.MASK_SIZE_BITS.toInt()
                requiredPropsByMask[maskIdx].add(prop)
            }
        }

        for (i in 0 until maskCount) {
            val reqMask = requiredMasks[i]
            if (reqMask != 0L) {
                val reqMaskStr = formatMaskString(reqMask)

                val propsInMask = requiredPropsByMask[i]

                if (propsInMask.size == 1) {
                    val prop = propsInMask[0]
                    val index = propertyIndices[prop]!!
                    val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                    val bitMask = 1L shl bitIdx
                    val bitMaskStr = formatMaskString(bitMask)

                    body.beginControlFlow(
                        C.TEMPLATE_IF_MASK_ZERO_SIMPLE,
                        i,
                        bitMaskStr
                    )
                    body.addStatement(
                        C.TEMPLATE_THROW_S,
                        C.STR_REQ_FIELD_1 + prop.jsonName + C.STR_REQ_FIELD_2
                    )
                    body.endControlFlow()
                } else {
                    body.beginControlFlow(
                        C.TEMPLATE_IF_MASK_NOT_MET_SIMPLE,
                        i,
                        reqMaskStr,
                        reqMaskStr
                    )
                    propsInMask.forEachIndexed { propIdx, prop ->
                        val index = propertyIndices[prop]!!
                        val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                        val bitMask = 1L shl bitIdx
                        val bitMaskStr = formatMaskString(bitMask)

                        if (propIdx == 0) {
                            body.beginControlFlow(
                                C.TEMPLATE_IF_MASK_ZERO_SIMPLE,
                                i,
                                bitMaskStr
                            )
                        } else {
                            body.nextControlFlow(
                                C.TEMPLATE_ELSE_IF_MASK_ZERO_SIMPLE,
                                i,
                                bitMaskStr
                            )
                        }
                        body.addStatement(
                            C.TEMPLATE_THROW_S,
                            C.STR_REQ_FIELD_1 + prop.jsonName + C.STR_REQ_FIELD_2
                        )
                    }
                    body.endControlFlow()
                    body.endControlFlow()
                }
            }
        }
    }

    /**
     * Emits the class instantiation return statement.
     */
    private fun emitReturnStatement(body: CodeBlock.Builder) {
        val hasDefaults = properties.any { it.hasDefaultValue }

        if (!hasDefaults) {
            body.addStatement(
                C.TEMPLATE_RETURN_T_PAREN,
                originalClassName
            )
            properties.forEach { prop ->
                body.addStatement(
                    C.TEMPLATE_NAMED_ARG,
                    prop.kotlinName,
                    prop.getReturnExpression()
                )
            }
            body.addStatement(C.STR_PAREN)
            return
        }

        emitDefaultValueReturn(body)
    }

    /**
     * Emits the optimal return strategy when some properties have default values.
     *
     * - N ≤ [C.MAX_DEFAULT_BRANCH_COUNT]: 2^N explicit constructor branches — 1 allocation, 0 copies.
     * - N >  [C.MAX_DEFAULT_BRANCH_COUNT]: val _result + .copy() — 1 or 2 allocations.
     *
     * The branch conditions are pure Long bitwise comparisons on local mask variables already
     * held in CPU registers, so their overhead is negligible compared to eliminating .copy().
     */
    private fun emitDefaultValueReturn(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        val defaultPropsWithIndex = properties.mapIndexedNotNull { globalIdx, prop ->
            if (prop.hasDefaultValue) Pair(globalIdx, prop) else null
        }

        if (defaultPropsWithIndex.size <= C.MAX_DEFAULT_BRANCH_COUNT) {
            emitMultiBranchReturn(body, requiredProps, defaultPropsWithIndex)
        } else {
            emitCopyReturn(body, requiredProps, defaultPropsWithIndex)
        }
    }

    /**
     * Generates 2^N if-branches, each calling the primary constructor exactly once.
     * Subsets are ordered by descending popcount so larger subsets take priority —
     * early-return semantics ensure correctness without needing negative checks.
     *
     * Example for 2 defaults (optA, optB):
     *   if ((_mask0 and 3L) == 3L) return MyClass(req, optA, optB)  // both present
     *   if ((_mask0 and 1L) == 1L) return MyClass(req, optA)        // optA only
     *   if ((_mask0 and 2L) == 2L) return MyClass(req, optB)        // optB only
     *   return MyClass(req)                                          // neither
     */
    private fun emitMultiBranchReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>
    ) {
        val n = defaultPropsWithIndex.size

        // Iterate subsets from most bits set → fewest; skip the empty set (handled as fallback).
        val subsets = (1 until (1 shl n))
            .sortedByDescending { mask -> (0 until n).sumOf { bit -> (mask shr bit) and 1 } }

        for (subsetBits in subsets) {
            val conditionStr = buildSubsetCondition(subsetBits, defaultPropsWithIndex)
            body.beginControlFlow("if ($conditionStr)")
            body.addStatement(C.TEMPLATE_RETURN_T_PAREN, originalClassName)
            requiredProps.forEach { prop ->
                body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
            }
            for (i in 0 until n) {
                if (subsetBits and (1 shl i) != 0) {
                    val (_, prop) = defaultPropsWithIndex[i]
                    body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
                }
            }
            body.addStatement(C.STR_PAREN)
            body.endControlFlow()
        }

        // Fallback: no default props present — omit them so Kotlin uses their default values.
        body.addStatement(C.TEMPLATE_RETURN_T_PAREN, originalClassName)
        requiredProps.forEach { prop ->
            body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
        }
        body.addStatement(C.STR_PAREN)
    }

    /**
     * Builds the condition string for a subset bitmask of default props.
     * Props are grouped by mask index; combined bits for each mask are compared with ==
     * so a single operation verifies all bits in that mask simultaneously.
     *
     * Example subset {optA=bit0, optB=bit1} both in mask0:
     *   returns "(_mask0 and 3L) == 3L"
     */
    private fun buildSubsetCondition(
        subsetBits: Int,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>
    ): String {
        val maskGroups = mutableMapOf<Int, Long>()
        for (i in defaultPropsWithIndex.indices) {
            if (subsetBits and (1 shl i) != 0) {
                val globalIdx = defaultPropsWithIndex[i].first
                val maskIdx = globalIdx / C.MASK_SIZE_BITS.toInt()
                val bitIdx = globalIdx % C.MASK_SIZE_BITS.toInt()
                maskGroups[maskIdx] = (maskGroups[maskIdx] ?: 0L) or (1L shl bitIdx)
            }
        }
        return maskGroups.entries.joinToString(C.STR_AND_AND) { (maskIdx, combinedBits) ->
            val bitsStr = formatMaskString(combinedBits)
            "(_mask$maskIdx and $bitsStr) == $bitsStr"
        }
    }

    /**
     * Legacy copy-based return for classes with > MAX_DEFAULT_BRANCH_COUNT default props.
     * Kept as-is since 2^N branches would produce excessive code bloat at that scale.
     */
    private fun emitCopyReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>
    ) {
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
        requiredProps.forEach { prop ->
            val expr = if (prop.isNullable) {
                C.STR_UNDERSCORE + prop.kotlinName
            } else {
                C.STR_UNDERSCORE + prop.kotlinName + C.STR_BANG_BANG
            }
            body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, expr)
        }
        body.addStatement(C.STR_PAREN)

        if (defaultPropsWithIndex.isNotEmpty()) {
            body.add(C.STR_IF_OPEN)
            val conditions = mutableListOf<String>()
            for (i in defaultMasks.indices) {
                val defMask = defaultMasks[i]
                if (defMask != 0L) {
                    val defMaskStr = formatMaskString(defMask)
                    conditions.add(C.TEMPLATE_MASK_CHECK_MATCH.format(i, defMaskStr))
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            defaultPropsWithIndex.forEach { (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val bitIdx = propIndex % C.MASK_SIZE_BITS.toInt()
                val bitMask = 1L shl bitIdx
                val bitMaskStr = formatMaskString(bitMask)
                val valueExpr = prop.getDefaultValueReturnExpression(maskIdx, bitMaskStr)
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
