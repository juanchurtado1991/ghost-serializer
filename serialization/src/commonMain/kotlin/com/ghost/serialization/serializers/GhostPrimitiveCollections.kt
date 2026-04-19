package com.ghostserializer.serializers

internal class GhostIntList(initialCapacity: Int = 16) {
    private var buffer = IntArray(initialCapacity)
    private var currentSize = 0
    
    fun add(value: Int) {
        if (currentSize == buffer.size) {
            buffer = buffer.copyOf(buffer.size shl 1)
        }
        buffer[currentSize++] = value
    }
    
    fun toArray(): IntArray {
        if (currentSize == buffer.size) return buffer
        return buffer.copyOf(currentSize)
    }

    fun isEmpty(): Boolean = currentSize == 0
}

internal class GhostLongList(initialCapacity: Int = 16) {
    private var buffer = LongArray(initialCapacity)
    private var currentSize = 0
    
    fun add(value: Long) {
        if (currentSize == buffer.size) {
            buffer = buffer.copyOf(buffer.size shl 1)
        }
        buffer[currentSize++] = value
    }
    
    fun toArray(): LongArray {
        if (currentSize == buffer.size) return buffer
        return buffer.copyOf(currentSize)
    }

    fun isEmpty(): Boolean = currentSize == 0
}
