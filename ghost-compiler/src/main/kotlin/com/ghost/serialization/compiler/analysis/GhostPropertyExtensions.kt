package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.TypeName
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Extension methods for [GhostPropertyModel] used during codegen to compute variable types,
 * initial values, and return expressions from a property's metadata.
 */

/**
 * Resolves the [TypeName] for this property's local tracking variable: unboxed for
 * non-nullable primitives and inline/value classes, nullable otherwise.
 */
internal fun GhostPropertyModel.getVariableType(): TypeName {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    return when {
        isPrimitive -> typeName
        isUnboxedValueClass -> {
            val underlying = valueClassProperty!!
            if (underlying.type.isPrimitive()) {
                underlying.typeName
            } else {
                underlying.typeName.copy(nullable = true)
            }
        }

        else -> typeName.copy(nullable = true)
    }
}

/**
 * Resolves the initial placeholder value literal representation for the tracking variable.
 * For example: `null` for objects, `0` for Int, `0L` for Long, `false` for Boolean.
 */
internal fun GhostPropertyModel.getInitialValue(): String {
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val targetProp = if (isUnboxedValueClass) valueClassProperty!! else this

    return when {
        isNullable -> C.STR_NULL
        targetProp.type.isPrimitiveInt() -> C.STR_ZERO
        targetProp.type.isPrimitiveLong() -> C.STR_ZERO_L
        targetProp.type.isPrimitiveULong() -> C.STR_ZERO_UL
        targetProp.type.isPrimitiveDouble() -> C.STR_ZERO_D
        targetProp.type.isPrimitiveFloat() -> C.STR_ZERO_F
        targetProp.type.isPrimitiveByte() -> C.STR_ZERO
        targetProp.type.isPrimitiveShort() -> C.STR_ZERO
        targetProp.type.isPrimitiveChar() -> C.STR_CHAR_NULL_LITERAL
        targetProp.type.isPrimitiveBoolean() -> C.STR_FALSE
        else -> C.STR_NULL
    }
}

/**
 * Generates the expression string used to pass this property to the constructor
 * in the standard deserializer return statement. Appends a null-assertion operator `!!`
 * if the parameter is non-nullable but tracked as a nullable local variable.
 */
internal fun GhostPropertyModel.getReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val varName = localValueName()

    return when {
        isPrimitive -> varName
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) {
                C.STR_EMPTY
            } else {
                C.STR_BANG_BANG
            }
            C.TEMPLATE_WRAP_TYPE.format(typeName, "$varName$bang")
        }

        else -> {
            if (isNullable) {
                varName
            } else {
                "$varName${C.STR_BANG_BANG}"
            }
        }
    }
}

/**
 * Fallback return expression for standard deserialization: the parsed variable when the mask
 * bit is set, otherwise the copy-based result field value.
 *
 * @param maskIdx Index of the tracking bitmask variable (e.g. `_mask0`).
 * @param bitMaskStr String representation of the bitmask representing this property.
 */
internal fun GhostPropertyModel.getDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = C.TEMPLATE_MASK_VAR.format(maskIdx)
    val varName = localValueName()
    val resultVar = C.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            C.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, varName, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) {
                C.STR_EMPTY
            } else {
                C.STR_BANG_BANG
            }
            C.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                C.TEMPLATE_WRAP_TYPE.format(typeName, "$varName$bang"),
                resultVar
            )
        }

        else -> {
            val value = "$varName${C.STR_BANG_BANG}"
            C.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                value,
                resultVar
            )
        }
    }
}

/**
 * True when this list of default properties is non-empty and every entry has a whitelisted
 * [GhostPropertyModel.defaultExpression]. Used to choose the single-ctor path over `.copy()`.
 */
internal fun List<GhostPropertyModel>.allDefaultsHaveExpressions(): Boolean =
    isNotEmpty() && all { it.defaultExpression != null }

/**
 * True when the whitelisted ctor default equals the local placeholder init
 * ([getInitialValue]), so `if (mask) parsed else default` collapses to just `parsed` — safe
 * because absent fields already leave the local at that same default.
 */
internal fun GhostPropertyModel.defaultMatchesLocalInit(): Boolean {
    val expr = defaultExpression ?: return false
    return expr == getInitialValue()
}

/**
 * Single-ctor arg for a default property: parsed value when the mask bit is set, otherwise the
 * whitelisted source default expression. Emits only the parsed local when
 * [defaultMatchesLocalInit] holds, since the mask ternary would be redundant.
 */
internal fun GhostPropertyModel.getSingleShotDefaultArgExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {
    if (defaultMatchesLocalInit()) {
        return getReturnExpression()
    }
    val expression = defaultExpression
        ?: error(C.STR_ERR_SINGLE_SHOT_DEFAULT_1 + kotlinName)
    val maskName = C.TEMPLATE_MASK_VAR.format(maskIdx)
    return C.TEMPLATE_IF_MASK_RETURN.format(
        maskName,
        bitMaskStr,
        getReturnExpression(),
        expression
    )
}

/**
 * Fragmented (ctx.*) variant of [getSingleShotDefaultArgExpression].
 */
internal fun GhostPropertyModel.getFragmentedSingleShotDefaultArgExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {
    if (defaultMatchesLocalInit()) {
        return getFragmentedReturnExpression()
    }
    val expression = defaultExpression
        ?: error(C.STR_ERR_SINGLE_SHOT_DEFAULT_1 + kotlinName)
    val maskName = C.TEMPLATE_CTX_MASK_VAR.format(maskIdx)
    return C.TEMPLATE_IF_MASK_RETURN.format(
        maskName,
        bitMaskStr,
        getFragmentedReturnExpression(),
        expression
    )
}

/**
 * Generates the return expression string pointing to the generated `DecodingContext`
 * during fragmented deserialization. Handles boxing/unboxing for value classes and nullability.
 */
internal fun GhostPropertyModel.getFragmentedReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val ctxVar = C.TEMPLATE_CTX_VAR.format(localTrackingName())

    return when {
        isPrimitive -> ctxVar
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) {
                C.STR_EMPTY
            } else {
                C.STR_BANG_BANG
            }

            C.TEMPLATE_WRAP_TYPE.format(typeName, "$ctxVar$bang")
        }

        else -> {
            if (isNullable) {
                ctxVar
            } else {
                "$ctxVar${C.STR_BANG_BANG}"
            }
        }
    }
}

/**
 * Fragmented variant of [getDefaultValueReturnExpression]: maps tracking mask checks directly
 * to fields on the `DecodingContext` instance.
 *
 * @param maskIdx Index of the tracking bitmask inside `DecodingContext`.
 * @param bitMaskStr String representation of the bitmask representing this property.
 */
internal fun GhostPropertyModel.getFragmentedDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {

    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = C.TEMPLATE_CTX_MASK_VAR.format(maskIdx)
    val ctxVar = C.TEMPLATE_CTX_VAR.format(localTrackingName())
    val resultVar = C.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            C.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, ctxVar, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) {
                C.STR_EMPTY
            } else {
                C.STR_BANG_BANG
            }
            C.TEMPLATE_WRAP_TYPE.format(
                typeName,
                C.TEMPLATE_IF_MASK_RETURN.format(
                    maskName,
                    bitMaskStr,
                    "$ctxVar$bang",
                    resultVar
                )

            )
        }

        else -> {
            val value = "$ctxVar${C.STR_BANG_BANG}"
            C.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                value,
                resultVar
            )
        }
    }
}
