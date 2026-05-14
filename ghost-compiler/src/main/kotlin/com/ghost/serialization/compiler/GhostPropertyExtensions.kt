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
    
    return when {
        isPrimitive -> "${C.STR_UNDERSCORE}$kotlinName"
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
            "$typeName(${C.STR_UNDERSCORE}$kotlinName$bang)"
        }
        else -> "${C.STR_UNDERSCORE}$kotlinName${C.STR_BANG_BANG}"
    }
}

internal fun GhostPropertyModel.getDefaultValueReturnExpression(maskIdx: Int, bitMaskStr: String): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    
    return when {
        isNullable -> "if ((_mask$maskIdx and $bitMaskStr) != 0L) _${kotlinName} else _result.${kotlinName}"
        isPrimitive -> "if ((_mask$maskIdx and $bitMaskStr) != 0L) _${kotlinName} else _result.${kotlinName}"
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
            "if ((_mask$maskIdx and $bitMaskStr) != 0L) ${typeName}(_${kotlinName}$bang) else _result.${kotlinName}"
        }
        else -> "if ((_mask$maskIdx and $bitMaskStr) != 0L) _${kotlinName}${C.STR_BANG_BANG} else _result.${kotlinName}"
    }
}

internal fun GhostPropertyModel.getFragmentedReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    
    return when {
        isPrimitive -> "ctx._$kotlinName"
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
            "$typeName(ctx._$kotlinName$bang)"
        }
        else -> "ctx._$kotlinName${C.STR_BANG_BANG}"
    }
}

internal fun GhostPropertyModel.getFragmentedDefaultValueReturnExpression(maskIdx: Int, bitMaskStr: String): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    
    return when {
        isNullable -> "if ((ctx._mask$maskIdx and $bitMaskStr) != 0L) ctx._${kotlinName} else _result.${kotlinName}"
        isPrimitive -> "if ((ctx._mask$maskIdx and $bitMaskStr) != 0L) ctx._${kotlinName} else _result.${kotlinName}"
        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.type.isPrimitive()) C.STR_EMPTY else C.STR_BANG_BANG
            "${typeName}(if ((ctx._mask$maskIdx and $bitMaskStr) != 0L) ctx._${kotlinName}$bang else _result.${kotlinName})"
        }
        else -> "if ((ctx._mask$maskIdx and $bitMaskStr) != 0L) ctx._${kotlinName}${C.STR_BANG_BANG} else _result.${kotlinName}"
    }
}
