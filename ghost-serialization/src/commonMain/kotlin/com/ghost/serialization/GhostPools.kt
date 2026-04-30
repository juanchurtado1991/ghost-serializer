package com.ghost.serialization

/**
 * Provides access to reusable buffers to minimize allocations during hot paths.
 */
@InternalGhostApi
expect fun acquireScratchBuffer(): ByteArray

/**
 * Releases a buffer back to the pool.
 */
@InternalGhostApi
expect fun releaseScratchBuffer(buffer: ByteArray)
