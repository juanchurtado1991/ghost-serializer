package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/** Growable [Long] buffer that avoids boxing. */
internal class GhostLongList(
    initialCapacity: Int = NUM.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY
) {
    private var buffer = LongArray(initialCapacity)
    private var currentSize = 0

    fun add(value: Long) {
        if (currentSize == buffer.size) {
            val newCapacity =
                if (buffer.isEmpty()) {
                    NUM.DEFAULT_PRIMITIVE_COLLECTION_CAPACITY
                } else {
                    (buffer.size * WR.BUFFER_SCALE_FACTOR)
                }
            buffer = buffer.copyOf(newSize = newCapacity)
        }
        buffer[currentSize++] = value
    }

    fun isEmpty(): Boolean {
        return currentSize == 0
    }

    fun toArray(): LongArray {
        if (currentSize == buffer.size) return buffer
        return buffer.copyOf(newSize = currentSize)
    }
}
