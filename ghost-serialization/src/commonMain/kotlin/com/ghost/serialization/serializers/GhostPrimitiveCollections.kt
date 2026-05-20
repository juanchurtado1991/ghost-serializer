package com.ghost.serialization.serializers

/**
 * Highly optimized, zero-allocation internal list implementation for [Int] primitives.
 * Avoids boxing overhead and memory allocation pressure.
 */
internal class GhostIntList(initialCapacity: Int = 16) {
    private var buffer = IntArray(initialCapacity)
    private var currentSize = 0

    /**
     * Adds an integer item to the list, expanding the backing buffer if necessary.
     */
    fun add(value: Int) {
        if (currentSize == buffer.size) {
            buffer = buffer.copyOf(buffer.size shl 1)
        }
        buffer[currentSize++] = value
    }

    /**
     * Returns the accumulated list elements as a raw [IntArray].
     */
    fun toArray(): IntArray {
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

/**
 * Highly optimized, zero-allocation internal list implementation for [Long] primitives.
 * Avoids boxing overhead and memory allocation pressure.
 */
internal class GhostLongList(initialCapacity: Int = 16) {
    private var buffer = LongArray(initialCapacity)
    private var currentSize = 0

    /**
     * Adds a long item to the list, expanding the backing buffer if necessary.
     */
    fun add(value: Long) {
        if (currentSize == buffer.size) {
            buffer = buffer.copyOf(buffer.size shl 1)
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
