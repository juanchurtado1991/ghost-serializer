package com.ghost.serialization.core.parser

import com.ghost.serialization.InternalGhostApi

/**
 * A high-performance data source abstraction for GhostJsonReader.
 * 
 * In Kotlin 2.3.21, this allows us to implement Zero-Copy reading for WASM
 * by wrapping the underlying JavaScript memory directly.
 */
@InternalGhostApi
interface GhostSource {
    val size: Int
    operator fun get(index: Int): Byte
    
    /**
     * Efficiently decodes a UTF-8 string from the source.
     * Implementations can use platform-specific optimizations like TextDecoder.
     */
    fun decodeToString(start: Int, end: Int): String
}

/**
 * Standard implementation for JVM and Native platforms.
 */
@InternalGhostApi
class ByteArraySource(val data: ByteArray) : GhostSource {
    override val size: Int get() = data.size
    override fun get(index: Int): Byte = data[index]
    override fun decodeToString(start: Int, end: Int): String = data.decodeToString(start, end)
}
