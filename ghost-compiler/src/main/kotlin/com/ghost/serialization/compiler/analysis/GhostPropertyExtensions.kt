package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.TypeName
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG


/**
 * Extension methods for [GhostPropertyModel] used during codegen to compute variable types,
 * initial values, and return expressions from a property's metadata.
 */

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
 * Fallback return expression: the parsed variable when the mask bit is set, otherwise the
 * copy-based result field value.
 *
 * @param maskIdx Index of the tracking bitmask variable (e.g. `_mask0`).
 * @param bitMaskStr Bitmask literal for this property.
 */
internal fun GhostPropertyModel.getDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = AC.TEMPLATE_MASK_VAR.format(maskIdx)
    val varName = localValueName()
    val resultVar = AC.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            AC.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, varName, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.holdsNullableOrPrimitive()) {
                CC.STR_EMPTY
            } else {
                AC.STR_BANG_BANG
            }
            AC.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                AC.TEMPLATE_WRAP_TYPE.format(typeName, "$varName$bang"),
                resultVar
            )
        }

        else -> {
            val value = "$varName${AC.STR_BANG_BANG}"
            AC.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                value,
                resultVar
            )
        }
    }
}

/**
 * Fragmented variant of [getDefaultValueReturnExpression].
 *
 * @param maskIdx Index of the tracking bitmask inside `DecodingContext`.
 * @param bitMaskStr Bitmask literal for this property.
 */
internal fun GhostPropertyModel.getFragmentedDefaultValueReturnExpression(
    maskIdx: Int,
    bitMaskStr: String
): String {

    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable

    val maskName = AC.TEMPLATE_CTX_MASK_VAR.format(maskIdx)
    val ctxVar = AC.TEMPLATE_CTX_VAR.format(localTrackingName())
    val resultVar = AC.TEMPLATE_RESULT_VAR.format(kotlinName)

    return when {
        isNullable || isPrimitive ->
            AC.TEMPLATE_IF_MASK_RETURN.format(maskName, bitMaskStr, ctxVar, resultVar)

        isUnboxedValueClass -> {
            val bang = if (valueClassProperty!!.holdsNullableOrPrimitive()) {
                CC.STR_EMPTY
            } else {
                AC.STR_BANG_BANG
            }
            AC.TEMPLATE_WRAP_TYPE.format(
                typeName,
                AC.TEMPLATE_IF_MASK_RETURN.format(
                    maskName,
                    bitMaskStr,
                    "$ctxVar$bang",
                    resultVar
                )

            )
        }

        else -> {
            val value = "$ctxVar${AC.STR_BANG_BANG}"
            AC.TEMPLATE_IF_MASK_RETURN.format(
                maskName,
                bitMaskStr,
                value,
                resultVar
            )
        }
    }
}

/**
 * Fragmented (`DecodingContext`) variant of [getReturnExpression].
 */
internal fun GhostPropertyModel.getFragmentedReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val ctxVar = AC.TEMPLATE_CTX_VAR.format(localTrackingName())

    return when {
        isPrimitive -> ctxVar
        isUnboxedValueClass -> {
            val unwrapped = if (valueClassProperty!!.holdsNullableOrPrimitive()) {
                ctxVar
            } else {
                requireLocalValue(local = ctxVar)
            }

            AC.TEMPLATE_WRAP_TYPE.format(typeName, unwrapped)
        }

        else -> {
            if (isNullable) {
                ctxVar
            } else {
                requireLocalValue(local = ctxVar)
            }
        }
    }
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
        ?: error(AC.STR_ERR_SINGLE_SHOT_DEFAULT_1 + kotlinName)
    val maskName = AC.TEMPLATE_CTX_MASK_VAR.format(maskIdx)
    return AC.TEMPLATE_IF_MASK_RETURN.format(
        maskName,
        bitMaskStr,
        getFragmentedReturnExpression(),
        expression
    )
}

/**
 * Resolves the initial placeholder value literal representation for the tracking variable.
 * For example: `null` for objects, `0` for Int, `0L` for Long, `false` for Boolean.
 */
internal fun GhostPropertyModel.getInitialValue(): String {
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val targetProp = if (isUnboxedValueClass) valueClassProperty!! else this

    return when {
        isNullable -> CC.STR_NULL
        targetProp.type.isPrimitiveInt() -> AC.STR_ZERO
        targetProp.type.isPrimitiveLong() -> AC.STR_ZERO_L
        targetProp.type.isPrimitiveULong() -> AC.STR_ZERO_UL
        targetProp.type.isPrimitiveDouble() -> AC.STR_ZERO_D
        targetProp.type.isPrimitiveFloat() -> AC.STR_ZERO_F
        targetProp.type.isPrimitiveByte() -> AC.STR_ZERO
        targetProp.type.isPrimitiveShort() -> AC.STR_ZERO
        targetProp.type.isPrimitiveChar() -> AC.STR_CHAR_NULL_LITERAL
        targetProp.type.isPrimitiveBoolean() -> CC.STR_FALSE
        else -> CC.STR_NULL
    }
}

/**
 * Constructor-arg expression for the standard deserializer return statement; appends `!!`
 * when the parameter is non-nullable but tracked as a nullable local variable.
 */
internal fun GhostPropertyModel.getReturnExpression(): String {
    val isPrimitive = type.isPrimitive() && !isNullable
    val isUnboxedValueClass = isValueClass && valueClassProperty != null && !isNullable
    val varName = localValueName()

    return when {
        isPrimitive -> varName
        isUnboxedValueClass -> {
            val unwrapped = if (valueClassProperty!!.holdsNullableOrPrimitive()) {
                varName
            } else {
                requireLocalValue(local = varName)
            }
            AC.TEMPLATE_WRAP_TYPE.format(typeName, unwrapped)
        }

        else -> {
            if (isNullable) {
                varName
            } else {
                requireLocalValue(local = varName)
            }
        }
    }
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
        ?: error(AC.STR_ERR_SINGLE_SHOT_DEFAULT_1 + kotlinName)
    val maskName = AC.TEMPLATE_MASK_VAR.format(maskIdx)
    return AC.TEMPLATE_IF_MASK_RETURN.format(
        maskName,
        bitMaskStr,
        getReturnExpression(),
        expression
    )
}

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
 * True when a missing JSON field must fail decoding: the property is a constructor parameter (a
 * property declared in the class body is written but cannot be set, so it is never required), is
 * non-nullable, has no constructor default, and is not a proto3 field whose zero value the
 * encoder omits.
 */
internal fun GhostPropertyModel.isRequiredOnWire(): Boolean =
    isInConstructor && !isNullable && !hasDefaultValue && protoOmittedZeroLiteral() == null

/**
 * True when the encoder may omit a proto3 field holding its zero value: the field has a
 * [protoZeroLiteral] and either no constructor default (a missing field decodes as the zero
 * value) or a default that is that same zero. A different default (`val active: Boolean = true`)
 * would turn an omitted zero into the default on decode, so such a field is always written.
 */
internal fun GhostPropertyModel.omitsProtoZeroOnEncode(): Boolean {
    val zeroLiteral = protoZeroLiteral() ?: return false
    if (!hasDefaultValue) {
        return true
    }
    val expression = defaultExpression ?: return false
    return when (zeroLiteral) {
        AC.STR_EMPTY_LIST_CALL, AC.STR_EMPTY_SET_CALL, AC.STR_EMPTY_MAP_CALL ->
            expression in AC.DEFAULT_EXPR_EMPTY_COLLECTION_CALLS

        CC.STR_FALSE, CG.STR_EMPTY_STRING, AC.STR_EMPTY_BYTE_ARRAY_CALL -> expression == zeroLiteral
        else -> expression.trimEnd(*AC.NUMERIC_LITERAL_SUFFIXES).toDoubleOrNull() == 0.0
    }
}

/**
 * The proto3 zero value, as Kotlin source, of a field that has no constructor default and is
 * omitted on encode when it holds that value; a missing field decodes as this value, so a
 * message reads back what it wrote (proto3 JSON treats an absent field as its zero value).
 */
internal fun GhostPropertyModel.protoOmittedZeroLiteral(): String? = protoZeroLiteral()?.takeIf { !hasDefaultValue }

/**
 * The proto3 zero value, as Kotlin source, of a `@GhostProtoSerialization` field whose type
 * has one (numbers, `Boolean`, `String`, `ByteArray`, collections, also inside a value class);
 * `null` for nullable fields, custom coders and types without a zero value.
 */
internal fun GhostPropertyModel.protoZeroLiteral(): String? {
    val hasZeroValueSemantics = isProto && !isNullable && customDecoder == null && customEncoder == null
    if (!hasZeroValueSemantics) {
        return null
    }
    val target = valueClassProperty?.takeIf { isValueClass } ?: this
    return when {
        target.isList -> AC.STR_EMPTY_LIST_CALL
        target.isSet -> AC.STR_EMPTY_SET_CALL
        target.isMap -> AC.STR_EMPTY_MAP_CALL
        else -> when (target.type.declaration.qualifiedName?.asString()) {
            AC.K_BYTE, AC.K_INT, AC.K_SHORT -> AC.STR_ZERO
            AC.K_BOOLEAN -> CC.STR_FALSE
            AC.K_BYTE_ARRAY -> AC.STR_EMPTY_BYTE_ARRAY_CALL
            AC.K_DOUBLE -> AC.STR_ZERO_D
            AC.K_FLOAT -> AC.STR_ZERO_F
            AC.K_LONG -> AC.STR_ZERO_L
            AC.K_STRING -> CG.STR_EMPTY_STRING
            AC.K_ULONG -> AC.STR_ZERO_UL
            else -> null
        }
    }
}

/** Non-null constructor argument for a non-nullable property tracked in a nullable [local]. */
internal fun GhostPropertyModel.requireLocalValue(
    local: String
): String {
    val zeroLiteral = protoOmittedZeroLiteral() ?: return local + AC.STR_BANG_BANG
    return AC.TEMPLATE_ELVIS_PARENTHESIZED.format(local, zeroLiteral)
}

/**
 * True when the encoder may skip a nullable property holding `null`: its constructor default is
 * `null`, so the omitted field decodes back to `null`. Any other default (`val age: Int? = 42`)
 * would turn the omitted `null` into that default, so `null` is written explicitly.
 */
internal fun GhostPropertyModel.omitsNullOnEncode(): Boolean = hasDefaultValue && defaultExpression == CC.STR_NULL

/** Whether a value-class property's local can be passed to the wrapper as is (no `!!`/default needed). */
internal fun GhostPropertyModel.holdsNullableOrPrimitive(): Boolean = isNullable || type.isPrimitive()
