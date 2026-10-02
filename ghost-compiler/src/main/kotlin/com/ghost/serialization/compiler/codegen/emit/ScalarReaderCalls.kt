package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.analysis.isPrimitiveBoolean
import com.ghost.serialization.compiler.analysis.isPrimitiveByte
import com.ghost.serialization.compiler.analysis.isPrimitiveChar
import com.ghost.serialization.compiler.analysis.isPrimitiveDouble
import com.ghost.serialization.compiler.analysis.isPrimitiveFloat
import com.ghost.serialization.compiler.analysis.isPrimitiveInt
import com.ghost.serialization.compiler.analysis.isPrimitiveLong
import com.ghost.serialization.compiler.analysis.isPrimitiveShort
import com.ghost.serialization.compiler.analysis.isPrimitiveULong
import com.ghost.serialization.compiler.analysis.isString
import com.google.devtools.ksp.symbol.KSType
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Reader call per Kotlin scalar type. [orNullCall] is the fused `nextXOrNull()` form (skips a
 * separate null-check branch for nullable scalars); types without one are wrapped in a generic
 * null guard by the emitter. [protoCall] replaces the plain call under proto3 (quoted int64).
 * Supporting another scalar type is one more entry here instead of another branch in
 * [BaseDeserializeEmitter]'s type dispatch.
 */
internal object ScalarReaderCalls {

    class Entry(
        val matches: (KSType) -> Boolean,
        val nonNullCall: String,
        val orNullCall: String? = null,
        val protoCall: String? = null,
    )

    private val entries = listOf(
        Entry(matches = KSType::isPrimitiveInt, nonNullCall = C.STR_NEXT_INT, orNullCall = C.STR_NEXT_INT_OR_NULL),
        Entry(
            matches = KSType::isPrimitiveBoolean,
            nonNullCall = C.STR_NEXT_BOOLEAN,
            orNullCall = C.STR_NEXT_BOOLEAN_OR_NULL
        ),
        Entry(
            matches = KSType::isPrimitiveLong,
            nonNullCall = C.STR_NEXT_LONG,
            orNullCall = C.STR_NEXT_LONG_OR_NULL,
            protoCall = C.STR_NEXT_LONG_PROTO_COERCED
        ),
        Entry(
            matches = KSType::isPrimitiveULong,
            nonNullCall = C.STR_NEXT_ULONG,
            orNullCall = C.STR_NEXT_ULONG_OR_NULL,
            protoCall = C.STR_NEXT_ULONG_PROTO_COERCED
        ),
        Entry(matches = KSType::isPrimitiveDouble, nonNullCall = C.STR_NEXT_DOUBLE),
        Entry(matches = KSType::isPrimitiveFloat, nonNullCall = C.STR_NEXT_FLOAT),
        Entry(matches = KSType::isPrimitiveByte, nonNullCall = C.STR_NEXT_BYTE),
        Entry(matches = KSType::isPrimitiveShort, nonNullCall = C.STR_NEXT_SHORT),
        Entry(matches = KSType::isPrimitiveChar, nonNullCall = C.STR_NEXT_CHAR),
        Entry(matches = KSType::isString, nonNullCall = C.STR_NEXT_STRING, orNullCall = C.STR_NEXT_STRING_OR_NULL),
    )

    fun find(type: KSType): Entry? = entries.firstOrNull { it.matches(type) }
}
