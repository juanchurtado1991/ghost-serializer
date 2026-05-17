package com.ghost.serialization

/**
 * Provides access to reusable buffers to minimize allocations during hot paths.
 * Tiered strategy handles small, medium, and large payloads efficiently.
 */
@InternalGhostApi
expect fun acquireScratchBuffer(minSize: Int = 48): ByteArray

/**
 * Releases a buffer back to the pool.
 */
@InternalGhostApi
expect fun releaseScratchBuffer(buffer: ByteArray)
