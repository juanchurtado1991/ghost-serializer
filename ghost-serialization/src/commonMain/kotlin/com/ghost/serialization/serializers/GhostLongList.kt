package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Highly optimized, zero-allocation internal list implementation for [Long] primitives.
 * Avoids boxing overhead and memory allocation pressure.
 */
internal class GhostLongList(initialCapacity: Int = C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY) {
    private var buffer = LongArray(initialCapacity)
    private var currentSize = 0

    fun add(value: Long) {
        if (currentSize == buffer.size) {
            val newCapacity =
                if (buffer.isEmpty()) {
                    C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY
                } else {
                    (buffer.size * C.BUFFER_SCALE_FACTOR)
                }
            buffer = buffer.copyOf(newCapacity)
        }
        buffer[currentSize++] = value
    }

    fun toArray(): LongArray {
        if (currentSize == buffer.size) {
            return buffer
        }
        return buffer.copyOf(currentSize)
    }

    fun isEmpty(): Boolean {
        return currentSize == 0
    }
}
