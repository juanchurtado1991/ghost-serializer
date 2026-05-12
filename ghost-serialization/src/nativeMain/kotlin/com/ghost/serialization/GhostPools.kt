package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE
import kotlin.native.concurrent.ThreadLocal

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

@ThreadLocal
private var small: ByteArray? = null

@ThreadLocal
private var medium: ByteArray? = null

@ThreadLocal
private var large: ByteArray? = null

@ThreadLocal
private var charSmall: CharArray? = null

@ThreadLocal
private var charMedium: CharArray? = null

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

@InternalGhostApi
actual fun acquireCharBuffer(minSize: Int): CharArray {
    return when {
        minSize <= TIER_SMALL -> {
            val localSmall = charSmall
            charSmall = null
            localSmall ?: CharArray(TIER_SMALL)
        }

        minSize <= TIER_MEDIUM -> {
            val localMedium = charMedium
            charMedium = null
            localMedium ?: CharArray(TIER_MEDIUM)
        }

        else -> CharArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseCharBuffer(buffer: CharArray) {
    val size = buffer.size
    when (size) {
        TIER_SMALL -> charSmall = buffer
        TIER_MEDIUM -> charMedium = buffer
    }
}
