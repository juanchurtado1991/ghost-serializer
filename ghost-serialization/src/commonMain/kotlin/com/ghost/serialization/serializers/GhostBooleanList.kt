package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Highly optimized, zero-allocation internal list implementation for [Boolean] primitives.
 * Avoids boxing overhead and memory allocation pressure.
 */
internal class GhostBooleanList(initialCapacity: Int = C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY) {
    private var buffer = BooleanArray(initialCapacity)
    private var currentSize = 0

    fun add(value: Boolean) {
        if (currentSize == buffer.size) {
            val newCapacity = if (buffer.isEmpty()) {
                C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY
            } else {
                (buffer.size * C.BUFFER_SCALE_FACTOR)
            }
            buffer = buffer.copyOf(newCapacity)
        }
        buffer[currentSize++] = value
    }

    fun toArray(): BooleanArray {
        if (currentSize == buffer.size) {
            return buffer
        }
        return buffer.copyOf(currentSize)
    }
}
