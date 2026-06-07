package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536
private const val TIER_XLARGE = 524288
private const val TIER_XXLARGE = 4194304

internal class GhostPool {
    var scratch: ByteArray? = null
    var small: ByteArray? = null
    var medium: ByteArray? = null
    var large: ByteArray? = null
    var xlarge: ByteArray? = null
    var xxlarge: ByteArray? = null
}

internal expect fun getLocalPool(): GhostPool

@PublishedApi
internal val SCRATCH_BUFFER_SIZE_INT = SCRATCH_BUFFER_SIZE

/**
 * Provides access to reusable buffers to minimize allocations during hot paths.
 * Tiered strategy handles small, medium, and large payloads efficiently.
 */
@InternalGhostApi
fun acquireScratchBuffer(minSize: Int = 48): ByteArray {
    val pool = getLocalPool()
    return when {
        minSize <= SCRATCH_BUFFER_SIZE_INT -> {
            val scratchLocal = pool.scratch
            if (scratchLocal != null && scratchLocal.size >= minSize) {
                pool.scratch = null
                scratchLocal
            } else {
                ByteArray(SCRATCH_BUFFER_SIZE_INT)
            }
        }

        minSize <= TIER_SMALL -> {
            val smallLocal = pool.small
            if (smallLocal != null && smallLocal.size >= minSize) {
                pool.small = null
                smallLocal
            } else {
                ByteArray(TIER_SMALL)
            }
        }

        minSize <= TIER_MEDIUM -> {
            val mediumLocal = pool.medium
            if (mediumLocal != null && mediumLocal.size >= minSize) {
                pool.medium = null
                mediumLocal
            } else {
                ByteArray(TIER_MEDIUM)
            }
        }

        minSize <= TIER_LARGE -> {
            val largeLocal = pool.large
            if (largeLocal != null && largeLocal.size >= minSize) {
                pool.large = null
                largeLocal
            } else {
                ByteArray(TIER_LARGE)
            }
        }

        minSize <= TIER_XLARGE -> {
            val xlargeLocal = pool.xlarge
            if (xlargeLocal != null && xlargeLocal.size >= minSize) {
                pool.xlarge = null
                xlargeLocal
            } else {
                ByteArray(TIER_XLARGE)
            }
        }

        minSize <= TIER_XXLARGE -> {
            val xxlargeLocal = pool.xxlarge
            if (xxlargeLocal != null && xxlargeLocal.size >= minSize) {
                pool.xxlarge = null
                xxlargeLocal
            } else {
                ByteArray(TIER_XXLARGE)
            }
        }

        else -> ByteArray(minSize)
    }
}

/**
 * Releases a buffer back to the pool.
 */
@InternalGhostApi
fun releaseScratchBuffer(buffer: ByteArray) {
    val pool = getLocalPool()
    val size = buffer.size
    when (size) {
        SCRATCH_BUFFER_SIZE -> pool.scratch = buffer
        TIER_SMALL -> pool.small = buffer
        TIER_MEDIUM -> pool.medium = buffer
        TIER_LARGE -> pool.large = buffer
        TIER_XLARGE -> pool.xlarge = buffer
        TIER_XXLARGE -> pool.xxlarge = buffer
    }
}
