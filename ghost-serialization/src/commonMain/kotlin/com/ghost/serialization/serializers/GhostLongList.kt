package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Highly optimized, zero-allocation internal list implementation for [Long] primitives.
 * Avoids boxing overhead and memory allocation pressure.
 */
internal class GhostLongList(initialCapacity: Int = C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY) {
    private var buffer = LongArray(initialCapacity)
    private var currentSize = 0

    /**
     * Adds a long item to the list, expanding the backing buffer if necessary.
     */
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

    /**
     * Returns the accumulated list elements as a raw [LongArray].
     */
    fun toArray(): LongArray {
        if (currentSize == buffer.size) {
            return buffer
        }
        return buffer.copyOf(currentSize)
    }

    /**
     * Returns true if the collection has no elements.
     */
    fun isEmpty(): Boolean {
        return currentSize == 0
    }
}
