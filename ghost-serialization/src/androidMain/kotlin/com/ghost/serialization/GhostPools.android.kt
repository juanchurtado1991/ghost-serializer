package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private val pool = ThreadLocal<AndroidPool>()

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    var localPool = pool.get()
    if (localPool == null) {
        localPool = AndroidPool()
        pool.set(localPool)
    }
    return when {
        minSize <= SCRATCH_BUFFER_SIZE -> {
            val smallLocal = localPool.small
            if (smallLocal != null && smallLocal.size >= SCRATCH_BUFFER_SIZE) {
                localPool.small = null
                smallLocal
            } else {
                ByteArray(SCRATCH_BUFFER_SIZE)
            }
        }
        minSize <= TIER_SMALL -> {
            val smallLocal = localPool.small
            localPool.small = null
            smallLocal ?: ByteArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val mediumLocal = localPool.medium
            localPool.medium = null
            mediumLocal ?: ByteArray(TIER_MEDIUM)
        }
        minSize <= TIER_LARGE -> {
            val largeLocal = localPool.large
            localPool.large = null
            largeLocal ?: ByteArray(TIER_LARGE)
        }
        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val localPool = pool.get() ?: return
    val size = buffer.size
    when (size) {
        SCRATCH_BUFFER_SIZE, TIER_SMALL -> localPool.small = buffer
        TIER_MEDIUM -> localPool.medium = buffer
        TIER_LARGE -> localPool.large = buffer
    }
}
