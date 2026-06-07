package com.ghost.serialization

import sun.misc.Unsafe

@InternalGhostApi
actual object GhostStringUtil {
    private const val UNSAFE_FIELD_NAME = "theUnsafe"
    private const val STRING_VALUE_FIELD_NAME = "value"
    private const val STRING_CODER_FIELD_NAME = "coder"
    
    private const val UNINITIALIZED_OFFSET = -1L
    private const val LATIN1_CODER_VALUE = 0.toByte()

    private val unsafe: Unsafe?
    private val valueOffset: Long
    private val coderOffset: Long

    init {
        var u: Unsafe? = null
        var vOff = UNINITIALIZED_OFFSET
        var cOff = UNINITIALIZED_OFFSET
        try {
            val field = Unsafe::class.java.getDeclaredField(UNSAFE_FIELD_NAME)
            field.isAccessible = true
            u = field.get(null) as Unsafe

            val valueField = String::class.java.getDeclaredField(STRING_VALUE_FIELD_NAME)
            vOff = u.objectFieldOffset(valueField)

            val coderField = String::class.java.getDeclaredField(STRING_CODER_FIELD_NAME)
            cOff = u.objectFieldOffset(coderField)
        } catch (e: Throwable) {
            // ignore, Java 8 or encapsulated module
        }
        unsafe = u
        valueOffset = vOff
        coderOffset = cOff
    }

    actual fun extractLatin1Bytes(s: String): ByteArray? {
        if (unsafe == null || valueOffset == UNINITIALIZED_OFFSET || coderOffset == UNINITIALIZED_OFFSET) return null
        val coder = unsafe.getByte(s, coderOffset)
        if (coder != LATIN1_CODER_VALUE) return null
        return unsafe.getObject(s, valueOffset) as? ByteArray
    }
}
