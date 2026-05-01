package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private class JvmPool {
    var small: ByteArray? = null
    var medium: ByteArray? = null
    var large: ByteArray? = null
    
    var charSmall: CharArray? = null
    var charMedium: CharArray? = null
}

private val pool = ThreadLocal.withInitial { JvmPool() }

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    val p = pool.get()
    return when {
        minSize <= GhostJsonConstants.SCRATCH_BUFFER_SIZE -> {
            // Special case for the legacy fixed-size scratch
            val b = p.small
            if (b != null && b.size >= GhostJsonConstants.SCRATCH_BUFFER_SIZE) {
                p.small = null
                b
            } else ByteArray(GhostJsonConstants.SCRATCH_BUFFER_SIZE)
        }
        minSize <= TIER_SMALL -> {
            val b = p.small
            p.small = null
            b ?: ByteArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val b = p.medium
            p.medium = null
            b ?: ByteArray(TIER_MEDIUM)
        }
        minSize <= TIER_LARGE -> {
            val b = p.large
            p.large = null
            b ?: ByteArray(TIER_LARGE)
        }
        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val p = pool.get()
    val size = buffer.size
    when {
        size == GhostJsonConstants.SCRATCH_BUFFER_SIZE || size == TIER_SMALL -> p.small = buffer
        size == TIER_MEDIUM -> p.medium = buffer
        size == TIER_LARGE -> p.large = buffer
    }
}

@InternalGhostApi
actual fun acquireCharBuffer(minSize: Int): CharArray {
    val p = pool.get()
    return when {
        minSize <= TIER_SMALL -> {
            val b = p.charSmall
            p.charSmall = null
            b ?: CharArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val b = p.charMedium
            p.charMedium = null
            b ?: CharArray(TIER_MEDIUM)
        }
        else -> CharArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseCharBuffer(buffer: CharArray) {
    val p = pool.get()
    val size = buffer.size
    when {
        size == TIER_SMALL -> p.charSmall = buffer
        size == TIER_MEDIUM -> p.charMedium = buffer
    }
}
