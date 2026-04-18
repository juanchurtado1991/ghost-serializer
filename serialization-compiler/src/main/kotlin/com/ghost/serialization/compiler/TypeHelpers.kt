package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ksp.toTypeName

private fun KSType.nonNullTypeName() = toTypeName().copy(nullable = false)

internal fun KSType.isPrimitiveInt(): Boolean = nonNullTypeName() == INT
internal fun KSType.isPrimitiveBoolean(): Boolean = nonNullTypeName() == BOOLEAN
internal fun KSType.isPrimitiveLong(): Boolean = nonNullTypeName() == LONG
internal fun KSType.isPrimitiveDouble(): Boolean = nonNullTypeName() == DOUBLE
internal fun KSType.isPrimitiveFloat(): Boolean = nonNullTypeName() == FLOAT
internal fun KSType.isPrimitive(): Boolean = isPrimitiveInt() ||
        isPrimitiveBoolean() ||
        isPrimitiveLong() ||
        isPrimitiveDouble() ||
        isPrimitiveFloat()