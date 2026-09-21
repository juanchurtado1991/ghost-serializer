package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C

/** Growable [Int] buffer that avoids boxing. */
internal class GhostIntList(initialCapacity: Int = C.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY) {
    private var buffer = IntArray(initialCapacity)
    private var currentSize = 0

    fun add(value: Int) {
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

    fun toArray(): IntArray {
        if (currentSize == buffer.size) {
            return buffer
        }
        return buffer.copyOf(currentSize)
    }

    fun isEmpty(): Boolean {
        return currentSize == 0
    }
}
