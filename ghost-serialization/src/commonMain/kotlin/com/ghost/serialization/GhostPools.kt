package com.ghost.serialization

import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.SCRATCH_BUFFER_SIZE

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

/** Acquires a reusable buffer of at least [minSize] from the tiered pool, minimizing hot-path allocations. */
@InternalGhostApi
fun acquireScratchBuffer(
    minSize: Int = SCRATCH_BUFFER_SIZE
): ByteArray {
    val pool = getLocalPool()
    return when {
        minSize <= SCRATCH_BUFFER_SIZE_INT -> {
            val scratchLocal = pool.scratch
            if (scratchLocal != null && scratchLocal.size >= minSize) {
                pool.scratch = null
                scratchLocal
            } else {
                ByteArray(size = SCRATCH_BUFFER_SIZE_INT)
            }
        }

        minSize <= TIER_SMALL -> {
            val smallLocal = pool.small
            if (smallLocal != null && smallLocal.size >= minSize) {
                pool.small = null
                smallLocal
            } else {
                ByteArray(size = TIER_SMALL)
            }
        }

        minSize <= TIER_MEDIUM -> {
            val mediumLocal = pool.medium
            if (mediumLocal != null && mediumLocal.size >= minSize) {
                pool.medium = null
                mediumLocal
            } else {
                ByteArray(size = TIER_MEDIUM)
            }
        }

        minSize <= TIER_LARGE -> {
            val largeLocal = pool.large
            if (largeLocal != null && largeLocal.size >= minSize) {
                pool.large = null
                largeLocal
            } else {
                ByteArray(size = TIER_LARGE)
            }
        }

        minSize <= TIER_XLARGE -> {
            val xlargeLocal = pool.xlarge
            if (xlargeLocal != null && xlargeLocal.size >= minSize) {
                pool.xlarge = null
                xlargeLocal
            } else {
                ByteArray(size = TIER_XLARGE)
            }
        }

        minSize <= TIER_XXLARGE -> {
            val xxlargeLocal = pool.xxlarge
            if (xxlargeLocal != null && xxlargeLocal.size >= minSize) {
                pool.xxlarge = null
                xxlargeLocal
            } else {
                ByteArray(size = TIER_XXLARGE)
            }
        }

        else -> ByteArray(size = minSize)
    }
}

/** Releases a buffer back to the pool. */
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

/** Initial size (512 KiB) for response-body scratch buffers — equal to a pool tier, so it round-trips through the pool. */
@InternalGhostApi
const val RESPONSE_SCRATCH_INITIAL_SIZE = TIER_XLARGE

/**
 * Doubles [scratch]: acquires a buffer twice its size, copies the first [usedBytes] over, and
 * returns the old one to the pool. Shared by the Retrofit/Ktor response-body read loops.
 */
@InternalGhostApi
fun growScratchBuffer(scratch: ByteArray, usedBytes: Int): ByteArray {
    val grown = acquireScratchBuffer(minSize = scratch.size * 2)
    scratch.copyInto(grown, 0, 0, usedBytes)
    releaseScratchBuffer(buffer = scratch)
    return grown
}
