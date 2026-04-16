package com.ghost.serialization.serializers

internal class GhostIntList(initialCapacity: Int = 16) {
    private var data = IntArray(initialCapacity)
    private var size = 0
    
    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }
    
    fun toArray(): IntArray = data.copyOf(size)
    fun isEmpty(): Boolean = size == 0
}

internal class GhostLongList(initialCapacity: Int = 16) {
    private var data = LongArray(initialCapacity)
    private var size = 0
    
    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }
    
    fun toArray(): LongArray = data.copyOf(size)
    fun isEmpty(): Boolean = size == 0
}
