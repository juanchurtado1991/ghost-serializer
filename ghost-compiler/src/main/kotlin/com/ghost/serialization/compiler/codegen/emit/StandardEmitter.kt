package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.analysis.DispatchNamesResolver
import com.ghost.serialization.compiler.analysis.allDefaultsHaveExpressions
import com.ghost.serialization.compiler.analysis.getDefaultValueReturnExpression
import com.ghost.serialization.compiler.analysis.getInitialValue
import com.ghost.serialization.compiler.analysis.getReturnExpression
import com.ghost.serialization.compiler.analysis.getSingleShotDefaultArgExpression
import com.ghost.serialization.compiler.analysis.getVariableType
import com.ghost.serialization.compiler.analysis.isPrimitive
import com.ghost.serialization.compiler.analysis.localTrackingName
import com.ghost.serialization.compiler.analysis.localValueName
import com.ghost.serialization.compiler.codegen.GeneratedCallFormat
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Deserialization for classes within standard JVM limits (typically under 40 properties):
 * declares local tracking variables, emits the parse loop and validation bitmasks, then
 * instantiates the target class.
 */
internal class StandardEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName,
    private val supportsResilience: Boolean = true,
) : BaseDeserializeEmitter(properties = properties, originalClassName = originalClassName, readerClass = readerClass) {

    fun emit(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val requiredPropCount = properties.count { !it.isNullable && !it.hasDefaultValue }
        // Single-required validation uses MASK_<PROP> only; skip unused MASK_REQUIRED_N.
        emitPropertyMaskConstants(
            typeSpecBuilder = typeSpecBuilder,
            emitRequiredAggregateMasks = requiredPropCount > 1,
        )
        WrappedKeysEmitter.addWrappedKeyConstants(typeSpecBuilder = typeSpecBuilder, properties = properties)
        emitLocalVariables(body = body)
        WrappedKeysEmitter.emitCaptureVariables(body = body, properties = properties)
        emitMaskVariables(body = body)
        emitParseLoop(body = body)
        WrappedKeysEmitter.emitPostLoopDeserialization(
            body = body,
            properties = properties,
            propertyIndices = propertyIndices,
            readerClass = readerClass,
        )
        // Validate before endObject so JSONPath still includes the current frame for the error.
        emitFieldValidationCall(body = body)
        body.addStatement(C.STR_END_OBJECT)
        emitReturnStatement(body = body, typeSpecBuilder = typeSpecBuilder)

        emitValidationHelper(typeSpecBuilder = typeSpecBuilder)
    }

    /** Condition string for a subset bitmask of default props: checks all its bits are set in the mask. */
    private fun buildSubsetCondition(
        subsetBits: Int,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ): String {
        val maskGroups = mutableMapOf<Int, Long>()
        val defaultPropsInSubset = mutableListOf<GhostPropertyModel>()
        for (i in defaultPropsWithIndex.indices) {
            if (subsetBits and (CC.VAL_ONE shl i) != C.VAL_ZERO) {
                val pair = defaultPropsWithIndex[i]
                val globalIdx = pair.first
                val prop = pair.second
                defaultPropsInSubset.add(prop)
                val maskIdx = globalIdx / C.MASK_SIZE_BITS.toInt()
                val bitIdx = globalIdx % C.MASK_SIZE_BITS.toInt()
                maskGroups[maskIdx] =
                    (maskGroups[maskIdx] ?: C.VAL_ZERO_L) or (C.VAL_ONE_L shl bitIdx)
            }
        }
        val constName = C.STR_MASK_OPTS_PREFIX + defaultPropsInSubset
            .map { it.kotlinName.uppercase() }
            .sorted()
            .joinToString(CC.STR_UNDERSCORE)

        val combinedBits = maskGroups[0] ?: C.VAL_ZERO_L
        val bitsStr = formatMaskString(mask = combinedBits)
        if (typeSpecBuilder.propertySpecs.none { it.name == constName }) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(constName, com.squareup.kotlinpoet.LONG)
                    .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                    .initializer(C.TEMPLATE_L, bitsStr)
                    .build()
            )
        }
        return C.TEMPLATE_MASK_ALL_MET_SIMPLE.format(0, constName, constName)
    }

    /**
     * Instantiates the class once when every default has a whitelisted source expression;
     * otherwise falls back to required-ctor + `.copy(...)` so arbitrary Kotlin defaults stay correct.
     */
    private fun emitCopyReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        if (defaultPropsWithIndex.map { it.second }.allDefaultsHaveExpressions()) {
            emitSingleShotReturn(
                body = body,
                requiredProps = requiredProps,
                defaultPropsWithIndex = defaultPropsWithIndex
            )
        } else {
            emitLegacyCopyReturn(
                body = body,
                requiredProps = requiredProps,
                defaultPropsWithIndex = defaultPropsWithIndex,
                typeSpecBuilder = typeSpecBuilder
            )
        }
    }

    /** Private `createInstance` helper, used when default-property count exceeds `MAX_DEFAULT_BRANCH_COUNT`. */
    private fun emitCreateInstanceHelper(typeSpecBuilder: TypeSpec.Builder) {
        val hasCreateInstance =
            typeSpecBuilder.funSpecs.any { it.name == C.STR_FUN_CREATE_INSTANCE }
        if (hasCreateInstance) return

        val funBuilder = FunSpec.builder(C.STR_FUN_CREATE_INSTANCE)
            .addModifiers(KModifier.PRIVATE)
            .returns(originalClassName)

        for (i in 0 until maskCount) {
            funBuilder.addParameter(C.STR_MASK_INDEX_FMT.format(i), com.squareup.kotlinpoet.LONG)
        }

        properties.forEach { prop ->
            val varType = prop.getVariableType()
            funBuilder.addParameter(prop.localValueName(), varType)
        }

        val helperBody = CodeBlock.builder()
        val requiredProps = properties.filter { it.isInConstructor && !it.hasDefaultValue }
        val defaultPropsWithIndex = properties.mapIndexedNotNull { globalIdx, prop ->
            if (prop.isInConstructor && prop.hasDefaultValue) Pair(globalIdx, prop) else null
        }

        emitCopyReturn(
            body = helperBody,
            requiredProps = requiredProps,
            defaultPropsWithIndex = defaultPropsWithIndex,
            typeSpecBuilder = typeSpecBuilder
        )

        funBuilder.addCode(helperBody.build())
        typeSpecBuilder.addFunction(funBuilder.build())
    }

    private fun emitFieldValidationCall(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.isNullable && !it.hasDefaultValue }
        if (requiredProps.isNotEmpty()) {
            body.addStatement(
                C.TEMPLATE_CALL_VALIDATION,
                C.STR_FUN_VALIDATE_FIELDS,
                C.STR_PARAM_MASK0,
                C.STR_READER
            )
        }
    }

    /** Recursive nested parse loops for `@GhostFlatten` properties, matching the JSON's key hierarchy. */
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
            "${parentPrefix}${CC.STR_UNDERSCORE}$name"
        }

        val optionsName = CG.STR_OPTIONS_PREFIX + currentPrefix.uppercase()

        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        val subIndexName = "${C.STR_SUB_INDEX_PREFIX}$pathIndex"
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

            if (subProps.size > 1 && subProps.all { pathIndex >= fullPaths[propertyIndices[it]!!].size }) {
                throw IllegalStateException(
                    C.STR_ERR_FLATTEN_INFINITE_LOOP_1 + originalClassName +
                            C.STR_ERR_FLATTEN_INFINITE_LOOP_2 +
                            subProps.map { it.kotlinName + " -> " + it.jsonName }
                )
            }

            val isSingleLeafProperty = subProps.size == CC.VAL_ONE &&
                pathIndex >= fullPaths[propertyIndices[subProps[C.VAL_ZERO]]!!].size - CC.VAL_ONE
            if (isSingleLeafProperty) {
                emitPropertyAssignment(
                    body = body,
                    prop = subProps[C.VAL_ZERO],
                    index = propertyIndices[subProps[C.VAL_ZERO]]!!
                )
            } else {
                emitFlattenedGroup(
                    body = body,
                    name = subName,
                    props = subProps,
                    pathIndex = pathIndex + CC.VAL_ONE,
                    parentPrefix = currentPrefix
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
     * Required-args ctor (Kotlin applies real defaults) then optional `.copy(...)`.
     */
    private fun emitLegacyCopyReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
        requiredProps.forEach { prop ->
            val varName = prop.localValueName()
            val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
            val expr = if (prop.isNullable || isPrimitive) {
                varName
            } else {
                varName + AC.STR_BANG_BANG
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
                    val constName = emitDefaultMaskConstant(typeSpecBuilder = typeSpecBuilder, maskIndex = i)
                    conditions.add(C.TEMPLATE_MASK_CHECK_MATCH.format(i, constName))
                }
            }
            body.add(conditions.joinToString(C.STR_OR))
            body.beginControlFlow(CG.STR_CLOSE_PAREN_FLOW)

            body.addStatement(C.STR_RETURN_RESULT_COPY)
            defaultPropsWithIndex.forEach { (propIndex, prop) ->
                val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
                val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
                val valueExpr = prop.getDefaultValueReturnExpression(maskIdx = maskIdx, bitMaskStr = constName)
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

    private fun emitLocalVariables(body: CodeBlock.Builder) {
        properties.forEach {
            val varType = it.getVariableType()
            val initialValue = it.getInitialValue()
            body.addStatement(
                C.TEMPLATE_VAR_VALUE_DECL,
                it.localTrackingName(),
                varType,
                initialValue
            )
        }
    }

    private fun emitMaskVariables(body: CodeBlock.Builder) {
        for (i in C.VAL_ZERO until maskCount) {
            body.addStatement(C.STR_MASK_INIT, i)
        }
    }

    /**
     * Generates 2^N if-branches, one primary-constructor call per subset of present default
     * values, ordered most-bits-set to fewest, falling back to Kotlin's own defaults when none apply.
     */
    private fun emitMultiBranchReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>,
        typeSpecBuilder: TypeSpec.Builder
    ) {
        val size = defaultPropsWithIndex.size

        // Iterate subsets from most bits set → fewest; skip the empty set (handled as fallback).
        val subsets = (CC.VAL_ONE until (CC.VAL_ONE shl size))
            .sortedByDescending { mask -> (C.VAL_ZERO until size).sumOf { bit -> (mask shr bit) and CC.VAL_ONE } }

        for (subsetBits in subsets) {
            val conditionStr =
                buildSubsetCondition(
                    subsetBits = subsetBits,
                    defaultPropsWithIndex = defaultPropsWithIndex,
                    typeSpecBuilder = typeSpecBuilder
                )
            body.beginControlFlow(C.STR_IF_OPEN + conditionStr + CG.STR_CLOSE_PAREN_FLOW)
            body.addStatement(C.TEMPLATE_RETURN_T_PAREN, originalClassName)
            requiredProps.forEach { prop ->
                body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
            }
            for (i in C.VAL_ZERO until size) {
                if (subsetBits and (CC.VAL_ONE shl i) != C.VAL_ZERO) {
                    val (_, prop) = defaultPropsWithIndex[i]
                    body.addStatement(
                        C.TEMPLATE_NAMED_ARG,
                        prop.kotlinName,
                        prop.getReturnExpression()
                    )
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

    /** Main field parse loop via the perfect-hash options table; multi-depth fields go to [emitFlattenedGroup]. */
    private fun emitParseLoop(body: CodeBlock.Builder) {
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        val wrappedDispatch = WrappedKeysEmitter.wrappedKeyDispatch(properties = properties)
        val topLevelNames = DispatchNamesResolver.topLevelNames(properties = properties)
        val normalProperties = properties.filter { it.wrappedKeys == null }

        topLevelNames.forEachIndexed { topIndex, topName ->
            body.beginControlFlow(
                C.TEMPLATE_WHEN_BRANCH,
                topIndex
            )

            val wrappedEntry = wrappedDispatch[topName]
            if (wrappedEntry != null) {
                val (prop, keyIndex) = wrappedEntry
                WrappedKeysEmitter.emitWrappedKeyCapture(body = body, prop = prop, keyIndex = keyIndex)
            } else {
                val propsForThisName = normalProperties
                    .filter { fullPaths[propertyIndices[it]!!].first() == topName }

                val isSingleTopLevelProperty = propsForThisName.size == CC.VAL_ONE &&
                    fullPaths[propertyIndices[propsForThisName[C.VAL_ZERO]]!!].size == CC.VAL_ONE
                if (isSingleTopLevelProperty) {
                    emitPropertyAssignment(
                        body = body,
                        prop = propsForThisName[C.VAL_ZERO],
                        index = propertyIndices[propsForThisName[C.VAL_ZERO]]!!
                    )
                } else {
                    emitFlattenedGroup(
                        body = body,
                        name = topName,
                        props = propsForThisName,
                        pathIndex = CC.VAL_ONE,
                        parentPrefix = CC.STR_EMPTY
                    )
                }
            }
            body.endControlFlow()
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.beginControlFlow(C.STR_MINUS_TWO_ARROW)
        body.addStatement(C.STR_SKIP_VALUE)
        body.endControlFlow()
        body.endControlFlow()
        body.endControlFlow()
        // endObject is emitted after validation, in emit().
    }

    private fun emitPropertyAssignment(
        body: CodeBlock.Builder,
        prop: GhostPropertyModel,
        index: Int
    ) {
        val call = buildCall(prop = prop)
        val maskIdx = index / C.MASK_SIZE_BITS.toInt()
        val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()

        val varName = prop.localValueName()
        if (prop.isResilient && supportsResilience) {
            body.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
            body.addStatement(varName + C.TEMPLATE_ASSIGN_L, C.STR_IT)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
            body.endControlFlow()
        } else {
            body.addStatement(varName + C.TEMPLATE_ASSIGN_L, call)
            body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
        }
    }

    private fun emitReturnStatement(body: CodeBlock.Builder, typeSpecBuilder: TypeSpec.Builder) {
        val hasDefaults = properties.any { it.isInConstructor && it.hasDefaultValue }

        if (!hasDefaults) {
            body.addStatement(
                C.TEMPLATE_RETURN_T_PAREN,
                originalClassName
            )
            properties.filter { it.isInConstructor }.forEach { prop ->
                body.addStatement(
                    C.TEMPLATE_NAMED_ARG,
                    prop.kotlinName,
                    prop.getReturnExpression()
                )
            }
            body.addStatement(C.STR_PAREN)
            return
        }

        val defaultPropsWithIndex = properties.mapIndexedNotNull { globalIdx, prop ->
            if (prop.isInConstructor && prop.hasDefaultValue) Pair(globalIdx, prop) else null
        }

        if (defaultPropsWithIndex.size <= C.MAX_DEFAULT_BRANCH_COUNT) {
            val requiredProps = properties.filter { it.isInConstructor && !it.hasDefaultValue }
            emitMultiBranchReturn(
                body = body,
                requiredProps = requiredProps,
                defaultPropsWithIndex = defaultPropsWithIndex,
                typeSpecBuilder = typeSpecBuilder
            )
            return
        }

        emitCreateInstanceHelper(typeSpecBuilder = typeSpecBuilder)

        val args = mutableListOf<String>()
        for (i in 0 until maskCount) {
            args.add(C.STR_MASK_INDEX_FMT.format(i))
        }
        properties.forEach { prop ->
            args.add(prop.localValueName())
        }
        body.add("return ")
        body.add(GeneratedCallFormat.invoke(C.STR_FUN_CREATE_INSTANCE, args))
        body.add("\n")
    }

    /**
     * Single constructor call; each default arg is `if (maskN and BIT != 0L) parsed else default`.
     * Uses `val result = Type(...); return result` since KotlinPoet can't nest named-arg
     * `addStatement` calls under an open `return %T(` call.
     */
    private fun emitSingleShotReturn(
        body: CodeBlock.Builder,
        requiredProps: List<GhostPropertyModel>,
        defaultPropsWithIndex: List<Pair<Int, GhostPropertyModel>>
    ) {
        body.addStatement(C.TEMPLATE_VAL_RESULT, originalClassName)
        requiredProps.forEach { prop ->
            body.addStatement(C.TEMPLATE_NAMED_ARG, prop.kotlinName, prop.getReturnExpression())
        }
        defaultPropsWithIndex.forEach { (propIndex, prop) ->
            val maskIdx = propIndex / C.MASK_SIZE_BITS.toInt()
            val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
            body.addStatement(
                C.TEMPLATE_NAMED_ARG,
                prop.kotlinName,
                prop.getSingleShotDefaultArgExpression(maskIdx = maskIdx, bitMaskStr = constName)
            )
        }
        body.addStatement(C.STR_PAREN)
        body.addStatement(C.STR_RETURN_RESULT)
    }

    private fun emitValidationHelper(typeSpecBuilder: TypeSpec.Builder) {
        val requiredProps = properties.filter { !it.isNullable && !it.hasDefaultValue }
        if (requiredProps.isEmpty()) {
            return
        }

        val requiredMask0Name = C.STR_MASK_REQUIRED_0

        val funBuilder = FunSpec.builder(C.STR_FUN_VALIDATE_FIELDS)
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_PARAM_MASK0, com.squareup.kotlinpoet.LONG)
            .addParameter(C.STR_READER, readerClass)

        val funBody = CodeBlock.builder()
        if (requiredProps.size == 1) {
            val prop = requiredProps[0]
            val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()

            funBody.beginControlFlow(C.TEMPLATE_IF_MASK_ZERO_STMT, constName)
            funBody.addStatement(
                C.TEMPLATE_THROW_MISSING_REQUIRED,
                prop.jsonName
            )
            funBody.endControlFlow()
        } else {
            funBody.beginControlFlow(
                C.TEMPLATE_IF_MASK_NOT_MET_STMT,
                requiredMask0Name,
                requiredMask0Name
            )
            requiredProps.forEachIndexed { propIdx, prop ->
                val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()

                if (propIdx == 0) {
                    funBody.beginControlFlow(C.TEMPLATE_IF_MASK_ZERO_STMT, constName)
                } else {
                    funBody.nextControlFlow(C.TEMPLATE_ELSE_IF_MASK_ZERO_STMT_NEW, constName)
                }
                funBody.addStatement(
                    C.TEMPLATE_THROW_MISSING_REQUIRED,
                    prop.jsonName
                )
            }
            funBody.endControlFlow()
            funBody.endControlFlow()
        }

        funBuilder.addCode(funBody.build())
        typeSpecBuilder.addFunction(funBuilder.build())
    }
}
