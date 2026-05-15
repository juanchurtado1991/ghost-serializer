package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private val pool = ThreadLocal.withInitial { JvmPool() }

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    val pool = pool.get()
    return when {
        minSize <= SCRATCH_BUFFER_SIZE -> {
            // Special case for the legacy fixed-size scratch
            val small = pool.small
            if (small != null && small.size >= SCRATCH_BUFFER_SIZE) {
                pool.small = null
                small
            } else {
                ByteArray(SCRATCH_BUFFER_SIZE)
            }
        }
        minSize <= TIER_SMALL -> {
            val small = pool.small
            pool.small = null
            small ?: ByteArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val medium = pool.medium
            pool.medium = null
            medium ?: ByteArray(TIER_MEDIUM)
        }
        minSize <= TIER_LARGE -> {
            val large = pool.large
            pool.large = null
            large ?: ByteArray(TIER_LARGE)
        }
        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val pool = pool.get()
    val size = buffer.size
    when (size) {
        SCRATCH_BUFFER_SIZE, TIER_SMALL -> pool.small = buffer
        TIER_MEDIUM -> pool.medium = buffer
        TIER_LARGE -> pool.large = buffer
    }
}

@InternalGhostApi
actual fun acquireCharBuffer(minSize: Int): CharArray {
    val pool = pool.get()
    return when {
        minSize <= TIER_SMALL -> {
            val charSmall = pool.charSmall
            pool.charSmall = null
            charSmall ?: CharArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val charMedium = pool.charMedium
            pool.charMedium = null
            charMedium ?: CharArray(TIER_MEDIUM)
        }
        else -> CharArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseCharBuffer(buffer: CharArray) {
    val p = pool.get()
    val size = buffer.size
    when (size) {
        TIER_SMALL -> p.charSmall = buffer
        TIER_MEDIUM -> p.charMedium = buffer
    }
}
