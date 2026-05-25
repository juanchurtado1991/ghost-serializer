@file:Suppress("unused")

package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Emitter for standard deserialization logic.
 *
 * This emitter orchestrates code generation for classes that fit within standard JVM limits
 * (typically under 40 properties). It implements the strategy to declare local tracking variables,
 * initialize validation bitmasks, emit parsing loops for incoming JSON, validate required fields,
 * and instantiate the target class with an optimized return statement.
 *
 * @property properties The list of property models containing metadata for code generation.
 * @property originalClassName The target class class name to instantiate.
 * @property readerClass The reader class used for deserialization.
 */
internal class StandardEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    /**
     * Entry point to emit all standard deserialization code block builders.
     *
     * This method delegates the emission of variable declarations, parsing loops,
     * field validation checks, and DTO construction to specialized helper functions.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder] to append generated code to.
     * @param typeSpecBuilder The serializer class builder.
     */
    fun emit(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        emitPropertyMaskConstants(typeSpecBuilder)
        emitLocalVariables(body)
        emitMaskVariables(body)
        emitParseLoop(body)
        emitFieldValidationCall(body)
        emitReturnStatement(body, typeSpecBuilder)

        emitValidationHelper(typeSpecBuilder)
    }

    /**
     * Emits the local placeholder variable declarations for tracking property values.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     */
    private fun emitLocalVariables(body: CodeBlock.Builder) {
        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            body.addStatement(
                C.TEMPLATE_VAR_VALUE_DECL,
                it.kotlinName,
                varType,
                initialValue
            )
        }
    }

    /**
     * Emits the initialization declarations of bitmasks to track parsed properties.
     */
    private fun emitMaskVariables(body: CodeBlock.Builder) {
        for (i in C.VAL_ZERO until maskCount) {
            body.addStatement(C.STR_MASK_INIT, i)
        }
    }

    /**
     * Generates the main field parsing loop using the perfect hash options.
     *
     * This method writes:
     * - `reader.beginObject()`
     * - A `while (true)` loop with a `reader.selectNameAndConsume(OPTIONS)` index match.
     * - A `when (index)` routing table that dispatches fields based on pre-compiled indices.
     * Handles single-depth fields directly, and delegates multi-depth fields to [emitFlattenedGroup].
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
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
                propsForThisName.size == C.VAL_ONE &&
                fullPaths[propertyIndices[propsForThisName[C.VAL_ZERO]]!!].size == C.VAL_ONE
            ) {
                emitPropertyAssignment(
                    body,
                    propsForThisName[C.VAL_ZERO],
                    propertyIndices[propsForThisName[C.VAL_ZERO]]!!
                )
            } else {
                emitFlattenedGroup(
                    body,
                    topName,
                    propsForThisName,
                    C.VAL_ONE,
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
     *
     * This handles `@GhostFlatten` properties by nesting `beginObject()` loops corresponding
     * to the structured hierarchy of keys inside the JSON source.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param name The name of the current node in the property path hierarchy.
     * @param props The list of property models sharing this path prefix.
     * @param pathIndex The current depth level of recursion.
     * @param parentPrefix Cumulative path key prefix to locate nested perfect-hash lookup tables.
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
        val subIndexName = "subIndex$pathIndex"
        body.addStatement(C.STR_SELECT_SUB_NAME, subIndexName, optionsName)
        body.beginControlFlow(C.STR_WHEN_SUB_INDEX, subIndexName)

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
                subProps.size == C.VAL_ONE &&
                pathIndex == fullPaths[propertyIndices[subProps[C.VAL_ZERO]]!!].size - C.VAL_ONE
            ) {
                emitPropertyAssignment(
                    body,
                    subProps[C.VAL_ZERO],
                    propertyIndices[subProps[C.VAL_ZERO]]!!
                )
            } else {
                emitFlattenedGroup(
                    body,
                    subName,
                    subProps,
                    pathIndex + C.VAL_ONE,
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
     *
     * It reads the property from the JSON stream, updates the local placeholder variable,
     * and sets the corresponding bit in the tracking mask. If the property is marked as
     * resilient, it wraps the read in a resilient decoding block.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param prop The property model metadata.
     * @param index The global index of this property.
     */
    /**
     * Emits property value assignment statement and bitwise mask update.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param prop The property model.
     * @param index The global index of this property.
     */
    private fun emitPropertyAssignment(
        body: CodeBlock.Builder,
        prop: GhostPropertyModel,
        index: Int
    ) {
        val call = buildCall(prop)
        val maskIdx = index / C.MASK_SIZE_BITS.toInt()
        val constName = "MASK_" + prop.kotlinName.uppercase()

        val varName = C.TEMPLATE_VAR_NAME.format(prop.kotlinName)
        if (prop.isResilient) {
            body.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
            body.addStatement(varName + C.TEMPLATE_ASSIGN_L, C.STR_IT)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
            body.endControlFlow()
        } else {
            body.addStatement(varName + C.TEMPLATE_ASSIGN_L, call)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
        }
    }

    /**
     * Generates required field presence validation checks using bitwise operations.
     *
     * Groups required fields based on their mask index. If a single field in a mask is required,
     * it generates a simple bit-match check. If multiple fields in the same mask are required,
     * it evaluates the entire mask with a single bitwise comparison `(mask & reqMask) != reqMask`,
     * then routes inside to isolate and throw which specific field was missing.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     */
    /**
     * Emits a call to the descriptive private helper method validating that all required properties
     * were present in the parsed JSON stream.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     */
    private fun emitFieldValidationCall(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.isNullable && !it.hasDefaultValue }
        if (requiredProps.isNotEmpty()) {
            body.addStatement(C.TEMPLATE_CALL_VALIDATION, C.STR_FUN_VALIDATE_FIELDS, C.STR_PARAM_MASK0, C.STR_READER_VAR)
        }
    }

    /**
     * Generates a descriptive private helper method validating that all required properties
     * were present in the bitmask, throwing a GhostJsonException for any missing field.
     *
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitValidationHelper(typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { !it.isNullable && !it.hasDefaultValue }
        if (requiredProps.isEmpty()) {
            return
        }

        val requiredMask0Name = "MASK_REQUIRED_0"

        val funBuilder = FunSpec.builder(C.STR_FUN_VALIDATE_FIELDS)
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_PARAM_MASK0, com.squareup.kotlinpoet.LONG)
            .addParameter(C.STR_READER_VAR, readerClass)

        val funBody = CodeBlock.builder()
        if (requiredProps.size == 1) {
            val prop = requiredProps[0]
            val constName = "MASK_" + prop.kotlinName.uppercase()

            funBody.beginControlFlow(C.TEMPLATE_IF_MASK_ZERO_STMT, constName)
            funBody.addStatement(
                C.TEMPLATE_THROW_S,
                C.STR_REQ_FIELD_1 + prop.jsonName + C.STR_REQ_FIELD_2
            )
            funBody.endControlFlow()
        } else {
            funBody.beginControlFlow(C.TEMPLATE_IF_MASK_NOT_MET_STMT, requiredMask0Name, requiredMask0Name)
            requiredProps.forEachIndexed { propIdx, prop ->
                val constName = "MASK_" + prop.kotlinName.uppercase()

                if (propIdx == 0) {
                    funBody.beginControlFlow(C.TEMPLATE_IF_MASK_ZERO_STMT, constName)
                } else {
                    funBody.nextControlFlow(C.TEMPLATE_ELSE_IF_MASK_ZERO_STMT_NEW, constName)
                }
                funBody.addStatement(
                    C.TEMPLATE_THROW_S,
                    C.STR_REQ_FIELD_1 + prop.jsonName + C.STR_REQ_FIELD_2
                )
            }
            funBody.endControlFlow()
            funBody.endControlFlow()
        }

        funBuilder.addCode(funBody.build())
        typeSpecBuilder.addFunction(funBuilder.build())
    }

    /**
     * Emits the class instantiation return statement.
     *
     * If no properties have class-level default values, it outputs a direct constructor return.
     * If defaults exist, it delegates to [emitDefaultValueReturn] to choose the optimal dispatch strategy.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     */
    /**
     * Emits the return statements using optimal constructor dispatch or copy-based logic.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitReturnStatement(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
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

        emitDefaultValueReturn(body, typeSpecBuilder)
    }

    /**
     * Emits the optimal return strategy when some properties have default values.
     *
     * To support Kotlin's default argument logic, it decides whether to generate a multi-branch
     * return (no copy allocation overhead) or a copy-based return fallback (avoids code bloat).
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitDefaultValueReturn(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        val defaultPropsWithIndex = properties.mapIndexedNotNull { globalIdx, prop ->
            if (prop.hasDefaultValue) Pair(globalIdx, prop) else null
        }

        if (defaultPropsWithIndex.size <= C.MAX_DEFAULT_BRANCH_COUNT) {
            emitMultiBranchReturn(body, requiredProps, defaultPropsWithIndex, typeSpecBuilder)
        } else {
            emitCopyReturn(body, requiredProps, defaultPropsWithIndex, typeSpecBuilder)
        }
    }

    /**
     * Generates 2^N if-branches, each calling the primary constructor exactly once.
     *
     * Subsets of default values present in the input JSON are evaluated using bitmask checking.
     * Iterates subsets from most bits set to fewest. If all else fails, it calls the constructor
     * omitting all default properties so Kotlin's default values are used.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param requiredProps Required (non-default) property models.
     * @param defaultPropsWithIndex Property models with default values and their index.
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitMultiBranchReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        val size = defaultPropsWithIndex.size

        // Iterate subsets from most bits set → fewest; skip the empty set (handled as fallback).
        val subsets = (C.VAL_ONE until (C.VAL_ONE shl size))
            .sortedByDescending { mask -> (C.VAL_ZERO until size).sumOf { bit -> (mask shr bit) and C.VAL_ONE } }

        for (subsetBits in subsets) {
            val conditionStr = buildSubsetCondition(subsetBits, defaultPropsWithIndex, typeSpecBuilder)
            body.beginControlFlow(C.STR_IF_OPEN + conditionStr + C.STR_CLOSE_PAREN_FLOW)
            body.addStatement(C.TEMPLATE_RETURN_T_PAREN, originalClassName)
            requiredProps.forEach { prop ->
                body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
            }
            for (i in C.VAL_ZERO until size) {
                if (subsetBits and (C.VAL_ONE shl i) != C.VAL_ZERO) {
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
     *
     * Groups properties by their mask index and builds conditions checking if
     * all subset bits are active in the target mask.
     *
     * @param subsetBits Bitmask of default properties present in the subset.
     * @param defaultPropsWithIndex Default properties and their global indices.
     * @param typeSpecBuilder The serializer class builder.
     * @return Formatted boolean logic string.
     */
    private fun buildSubsetCondition(
        subsetBits: Int,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ): String {
        val maskGroups = mutableMapOf<Int, Long>()
        val defaultPropsInSubset = mutableListOf<GhostPropertyModel>()
        for (i in defaultPropsWithIndex.indices) {
            if (subsetBits and (C.VAL_ONE shl i) != C.VAL_ZERO) {
                val pair = defaultPropsWithIndex[i]
                val globalIdx = pair.first
                val prop = pair.second
                defaultPropsInSubset.add(prop)
                val maskIdx = globalIdx / C.MASK_SIZE_BITS.toInt()
                val bitIdx = globalIdx % C.MASK_SIZE_BITS.toInt()
                maskGroups[maskIdx] = (maskGroups[maskIdx] ?: C.VAL_ZERO_L) or (C.VAL_ONE_L shl bitIdx)
            }
        }
        val constName = "MASK_OPTS_" + defaultPropsInSubset.map { it.kotlinName.uppercase() }.sorted().joinToString("_")
        val combinedBits = maskGroups[0] ?: C.VAL_ZERO_L
        val bitsStr = formatMaskString(combinedBits)
        if (typeSpecBuilder.propertySpecs.none { it.name == constName }) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(constName, com.squareup.kotlinpoet.LONG)
                    .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                    .initializer("%L", bitsStr)
                    .build()
            )
        }
        return C.TEMPLATE_MASK_ALL_MET_SIMPLE.format(0, constName, constName)
    }

    /**
     * Legacy copy-based return for classes with > MAX_DEFAULT_BRANCH_COUNT default props.
     *
     * Instantiates the class with required arguments first, then calls `.copy(...)` on the
     * result for any default properties that were encountered, using if-statements to check their
     * corresponding bits.
     *
     * @param body The target KotlinPoet [CodeBlock.Builder].
     * @param requiredProps Required (non-default) property models.
     * @param defaultPropsWithIndex Property models with default values and their index.
     * @param typeSpecBuilder The serializer class builder.
     */
    private fun emitCopyReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
        requiredProps.forEach { prop ->
            val varName = C.TEMPLATE_VAR_NAME.format(prop.kotlinName)
            val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
            val expr = if (prop.isNullable || isPrimitive) {
                varName
            } else {
                varName + C.STR_BANG_BANG
            }
            body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, expr)
        }
        body.addStatement(C.STR_PAREN)

        if (defaultPropsWithIndex.isNotEmpty()) {
            body.add(C.STR_IF_OPEN)
            val conditions = mutableListOf<String>()
            for (i in defaultMasks.indices) {
                val defMask = defaultMasks[i]
                if (defMask != C.VAL_ZERO_L) {
                    val defMaskStr = formatMaskString(defMask)
                    val constName = "MASK_DEFAULTS_$i"
                    if (typeSpecBuilder.propertySpecs.none { it.name == constName }) {
                        typeSpecBuilder.addProperty(
                            PropertySpec.builder(constName, com.squareup.kotlinpoet.LONG)
                                .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                                .initializer("%L", defMaskStr)
                                .build()
                        )
                    }
                    conditions.add(C.TEMPLATE_MASK_CHECK_MATCH.format(i, constName))
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(C.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            defaultPropsWithIndex.forEach { (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val bitIdx = propIndex % C.MASK_SIZE_BITS.toInt()
                val bitMask = C.VAL_ONE_L shl bitIdx
                val bitMaskStr = formatMaskString(bitMask)
                val constName = "MASK_" + prop.kotlinName.uppercase()
                val valueExpr = prop.getDefaultValueReturnExpression(maskIdx, constName)
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
