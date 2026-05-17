package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.TypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Extension methods for [GhostPropertyModel] to facilitate code generation.
 *
 * These helpers encapsulate the logic for determining variable types, initial values,
 * and return expressions based on the property's metadata (e.g., nullability,
 * primitive types, value classes).
 */
internal fun GhostPropertyModel.getVariableType(): TypeName {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    return when {
        isPrimitive -> typeName
        isUnboxedValueClass -> {
            val underlying = valueClassProperty!!
            if (underlying.type.isPrimitive()) underlying.typeName
            else underlying.typeName.copy(nullable = true)
        }

        else -> typeName.copy(nullable = true)
    }
}

internal fun GhostPropertyModel.getInitialValue(): String {
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val targetProp = if (isUnboxedValueClass) valueClassProperty!! else this

    return when {
        isNullable -> C.STR_NULL
        targetProp.type.isPrimitiveInt() -> C.STR_ZERO
        targetProp.type.isPrimitiveLong() -> C.STR_ZERO_L
        targetProp.type.isPrimitiveDouble() -> C.STR_ZERO_D
        targetProp.type.isPrimitiveFloat() -> C.STR_ZERO_F
        targetProp.type.isPrimitiveBoolean() -> C.STR_FALSE
        else -> C.STR_NULL
    }
}

internal fun GhostPropertyModel.getReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val varName = C.TEMPLATE_VAR_NAME.format(kotlinName)

    return when {
        isPrimitive -> varName
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
            C.TEMPLATE_WRAP_TYPE.format(typeName, "$varName$bang")
        }

        else -> {
            if (isNullable) varName
            else "$varName${C.STR_BANG_BANG}"
        }
    }
}

internal fun GhostPropertyModel.getDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = C.TEMPLATE_MASK_VAR.format(maskIdx)
    val varName = C.TEMPLATE_VAR_NAME.format(kotlinName)
    val resultVar = C.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            C.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, varName, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
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

internal fun GhostPropertyModel.getFragmentedReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val ctxVar = C.TEMPLATE_CTX_VAR.format(kotlinName)

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
            if (isNullable) ctxVar
            else "$ctxVar${C.STR_BANG_BANG}"
        }
    }
}

internal fun GhostPropertyModel.getFragmentedDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {

    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = C.TEMPLATE_CTX_MASK_VAR.format(maskIdx)
    val ctxVar = C.TEMPLATE_CTX_VAR.format(kotlinName)
    val resultVar = C.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            C.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, ctxVar, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
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
