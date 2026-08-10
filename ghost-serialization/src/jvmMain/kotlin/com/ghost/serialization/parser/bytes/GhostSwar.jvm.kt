package com.ghost.serialization.parser.bytes

import java.lang.invoke.MethodHandles
import java.nio.ByteOrder


/**
 * Native-order `byte[]`→`long` view. Plain [java.lang.invoke.VarHandle.get] permits unaligned
 * access, letting the JIT emit a single (possibly misaligned) 64-bit load on x86/ARM.
 */
private val LONG_VIEW = MethodHandles.byteArrayViewVarHandle(
    LongArray::class.java,
    ByteOrder.nativeOrder(),
)

internal actual val ghostUseSwarScans: Boolean = true

internal actual fun ghostReadLong8(data: ByteArray, index: Int): Long =
    LONG_VIEW.get(data, index) as Long
