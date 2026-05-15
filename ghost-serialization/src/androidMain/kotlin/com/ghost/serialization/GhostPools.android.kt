package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonConstants.SCRATCH_BUFFER_SIZE

private const val TIER_SMALL = 1024
private const val TIER_MEDIUM = 16384
private const val TIER_LARGE = 65536

private val pool = ThreadLocal<AndroidPool>()

@InternalGhostApi
actual fun acquireScratchBuffer(minSize: Int): ByteArray {
    var pool = pool.get()
    if (pool == null) {
        pool = AndroidPool()
        com.ghost.serialization.pool.set(pool)
    }
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
        minSize <= TIER_SMALL -> {
            val smallLocal = pool.small
            pool.small = null
            smallLocal ?: ByteArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val mediumLocal = pool.medium
            pool.medium = null
            mediumLocal ?: ByteArray(TIER_MEDIUM)
        }
        minSize <= TIER_LARGE -> {
            val largeLocal = pool.large
            pool.large = null
            largeLocal ?: ByteArray(TIER_LARGE)
        }
        else -> ByteArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseScratchBuffer(buffer: ByteArray) {
    val pool = pool.get() ?: return
    val size = buffer.size
    when (size) {
        SCRATCH_BUFFER_SIZE, TIER_SMALL -> pool.small = buffer
        TIER_MEDIUM -> pool.medium = buffer
        TIER_LARGE -> pool.large = buffer
    }
}

@InternalGhostApi
actual fun acquireCharBuffer(minSize: Int): CharArray {
    var localPool = pool.get()
    if (localPool == null) {
        localPool = AndroidPool()
        pool.set(localPool)
    }
    return when {
        minSize <= TIER_SMALL -> {
            val char = localPool.charSmall
            localPool.charSmall = null
            char ?: CharArray(TIER_SMALL)
        }
        minSize <= TIER_MEDIUM -> {
            val char = localPool.charMedium
            localPool.charMedium = null
            char ?: CharArray(TIER_MEDIUM)
        }
        else -> CharArray(minSize)
    }
}

@InternalGhostApi
actual fun releaseCharBuffer(buffer: CharArray) {
    val pool = pool.get() ?: return
    val size = buffer.size
    when (size) {
        TIER_SMALL -> pool.charSmall = buffer
        TIER_MEDIUM -> pool.charMedium = buffer
    }
}
