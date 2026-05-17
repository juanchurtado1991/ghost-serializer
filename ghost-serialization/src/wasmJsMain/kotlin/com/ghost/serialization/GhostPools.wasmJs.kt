package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private var small: ByteArray? = null
private var medium: ByteArray? = null
private var large: ByteArray? = null

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    return when {
        minSize <= SCRATCH_BUFFER_SIZE -> {
            val localSmall = small
            if (
                localSmall != null &&
                localSmall.size >= SCRATCH_BUFFER_SIZE
            ) {
                small = null
                localSmall
            } else {
                ByteArray(SCRATCH_BUFFER_SIZE)
            }
        }

        minSize <= TIER_SMALL -> {
            val localSmall = small
            small = null
            localSmall ?: ByteArray(TIER_SMALL)
        }

        minSize <= TIER_MEDIUM -> {
            val localMedium = medium
            medium = null
            localMedium ?: ByteArray(TIER_MEDIUM)
        }

        minSize <= TIER_LARGE -> {
            val localLarge = large
            large = null
            localLarge ?: ByteArray(TIER_LARGE)
        }

        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val size = buffer.size
    when (size) {
        SCRATCH_BUFFER_SIZE, TIER_SMALL -> small = buffer
        TIER_MEDIUM -> medium = buffer
        TIER_LARGE -> large = buffer
    }
}
