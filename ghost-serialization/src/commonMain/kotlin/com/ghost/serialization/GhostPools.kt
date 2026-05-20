package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536
private const val TIER_XLARGE = 524288

internal class GhostPool {
    var small: ByteArray? = null
    var medium: ByteArray? = null
    var large: ByteArray? = null
    var xlarge: ByteArray? = null
}

internal expect fun getLocalPool(): GhostPool

private inline fun poolOrAllocate(
    tierSize: Int,
    get: () -> ByteArray?,
    set: (ByteArray?) -> Unit
): ByteArray {
    val cached = get()
    set(null)
    return cached ?: ByteArray(tierSize)
}

/**
 * Provides access to reusable buffers to minimize allocations during hot paths.
 * Tiered strategy handles small, medium, and large payloads efficiently.
 */
@InternalGhostApi
fun acquireScratchBuffer(minSize: Int = 48): ByteArray {
    val pool = getLocalPool()
    return when {
        minSize <= SCRATCH_BUFFER_SIZE -> {
            val smallLocal = pool.small
            if (smallLocal != null && smallLocal.size >= SCRATCH_BUFFER_SIZE) {
                pool.small = null
                smallLocal
            } else {
                ByteArray(SCRATCH_BUFFER_SIZE)
            }
        }

        minSize <= TIER_SMALL -> poolOrAllocate(
            TIER_SMALL,
            { pool.small },
            { pool.small = it }
        )

        minSize <= TIER_MEDIUM -> poolOrAllocate(
            TIER_MEDIUM,
            { pool.medium },
            { pool.medium = it }
        )

        minSize <= TIER_LARGE -> poolOrAllocate(
            TIER_LARGE,
            { pool.large },
            { pool.large = it }
        )

        minSize <= TIER_XLARGE -> poolOrAllocate(
            TIER_XLARGE,
            { pool.xlarge },
            { pool.xlarge = it }
        )

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
        SCRATCH_BUFFER_SIZE, TIER_SMALL -> pool.small = buffer
        TIER_MEDIUM -> pool.medium = buffer
        TIER_LARGE -> pool.large = buffer
        TIER_XLARGE -> pool.xlarge = buffer
    }
}
