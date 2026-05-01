package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private var small: ByteArray? = null
private var medium: ByteArray? = null
private var large: ByteArray? = null
private var charSmall: CharArray? = null
private var charMedium: CharArray? = null

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    return when {
        minSize <= GhostJsonConstants.SCRATCH_BUFFER_SIZE -> {
            val b = small
            if (b != null && b.size >= GhostJsonConstants.SCRATCH_BUFFER_SIZE) {
                small = null
                b
            } else ByteArray(GhostJsonConstants.SCRATCH_BUFFER_SIZE)
        }
        minSize <= TIER_SMALL -> {
            val b = small
            small = null
            b ?: ByteArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val b = medium
            medium = null
            b ?: ByteArray(TIER_MEDIUM)
        }
        minSize <= TIER_LARGE -> {
            val b = large
            large = null
            b ?: ByteArray(TIER_LARGE)
        }
        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val size = buffer.size
    when {
        size == GhostJsonConstants.SCRATCH_BUFFER_SIZE || size == TIER_SMALL -> small = buffer
        size == TIER_MEDIUM -> medium = buffer
        size == TIER_LARGE -> large = buffer
    }
}

@InternalGhostApi
actual fun acquireCharBuffer(minSize: Int): CharArray {
    return when {
        minSize <= TIER_SMALL -> {
            val b = charSmall
            charSmall = null
            b ?: CharArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val b = charMedium
            charMedium = null
            b ?: CharArray(TIER_MEDIUM)
        }
        else -> CharArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseCharBuffer(buffer: CharArray) {
    val size = buffer.size
    when {
        size == TIER_SMALL -> charSmall = buffer
        size == TIER_MEDIUM -> charMedium = buffer
    }
}
