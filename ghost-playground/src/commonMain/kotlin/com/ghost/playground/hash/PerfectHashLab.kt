package com.ghost.playground.hash

object PerfectHashLab {
    private const val HASH_MULTIPLIER_START = 31
    private const val HASH_MULTIPLIER_LIMIT = 15_000
    private const val HASH_MULTIPLIER_STEP = 2
    private const val HASH_SHIFT_LIMIT = 16
    private const val COLLISION_HASH_MULTIPLIER = 31
    private const val BYTE_MASK = 0xFF
    private const val BYTE_MASK_LONG = 0xFFL
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val SHIFT_24 = 24
    private const val SHIFT_32 = 32
    private const val EMPTY_SLOT = -1
    private const val TABLE_SIZE_128 = 128
    private const val TABLE_SIZE_256 = 256
    private const val TABLE_SIZE_512 = 512
    private const val TABLE_SIZE_1024 = 1024
    private const val TABLE_SIZE_2048 = 2048
    private const val TABLE_SIZE_4096 = 4096
    private const val TABLE_SIZE_8192 = 8192
    private const val EMPTY_TABLE_SIZE = TABLE_SIZE_128
    private val TABLE_SIZES = intArrayOf(
        TABLE_SIZE_128,
        TABLE_SIZE_256,
        TABLE_SIZE_512,
        TABLE_SIZE_1024,
        TABLE_SIZE_2048,
        TABLE_SIZE_4096,
        TABLE_SIZE_8192,
    )

    fun findPerfectHash(names: List<String>): PerfectHashConfig {
        if (names.isEmpty()) {
            return PerfectHashConfig(
                0,
                HASH_MULTIPLIER_START,
                EMPTY_TABLE_SIZE,
                extendedKeyHash = false
            )
        }
        findInternal(names, useExtendedKeyHash = false)?.let { return it.config }
        findInternal(names, useExtendedKeyHash = true)?.let { return it.config }
        error("Could not find a collision-free perfect hash for fields: ${names.joinToString()}")
    }

    /** Returns the hash configuration and dispatch indices (`-1` marks an empty slot). */
    fun dispatchTable(names: List<String>): Pair<PerfectHashConfig, IntArray> {
        if (names.isEmpty()) {
            return PerfectHashConfig(
                0,
                HASH_MULTIPLIER_START,
                EMPTY_TABLE_SIZE,
                extendedKeyHash = false
            ) to
                    IntArray(EMPTY_TABLE_SIZE) { EMPTY_SLOT }
        }
        findInternal(names, useExtendedKeyHash = false)?.let { return it.config to it.dispatch }
        findInternal(names, useExtendedKeyHash = true)?.let { return it.config to it.dispatch }
        error("Could not find a collision-free perfect hash for fields: ${names.joinToString()}")
    }

    /**
     * All dispatch slots plus a human-readable hash summary for the preview UI.
     * The full table must be returned: the minimum size is 128, and fields may hash
     * into slots above 64, so truncating the preview would hide occupied entries.
     */
    fun dispatchPreview(names: List<String>): Pair<List<DispatchSlot>, String> {
        val (cfg, table) = dispatchTable(names)
        val slots = List(cfg.tableSize) { slotIndex ->
            val fieldIndex = table.getOrNull(slotIndex) ?: EMPTY_SLOT
            val name = if (fieldIndex >= 0) names[fieldIndex] else null
            DispatchSlot(slotIndex, name, name != null)
        }
        val summary = buildString {
            append("table=${cfg.tableSize}, multiplier=${cfg.multiplier}, shift=${cfg.shift}")
            if (cfg.extendedKeyHash) append(", extended keys")
        }
        return slots to summary
    }

    private data class HashResult(val config: PerfectHashConfig, val dispatch: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as HashResult

            if (config != other.config) return false
            if (!dispatch.contentEquals(other.dispatch)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = config.hashCode()
            result = COLLISION_HASH_MULTIPLIER * result + dispatch.contentHashCode()
            return result
        }
    }

    private fun findInternal(names: List<String>, useExtendedKeyHash: Boolean): HashResult? {
        val rawBytes = names.map { it.encodeToByteArray() }
        val hasCollisions = useExtendedKeyHash || detectPrefixLengthCollisions(rawBytes)
        for (tableSize in TABLE_SIZES) {
            val tableMask = tableSize - 1
            for (multiplier in HASH_MULTIPLIER_START..HASH_MULTIPLIER_LIMIT step HASH_MULTIPLIER_STEP) {
                for (shift in 0..HASH_SHIFT_LIMIT) {
                    val dispatch = IntArray(tableSize) { EMPTY_SLOT }
                    var collision = false
                    for (index in rawBytes.indices) {
                        val bytes = rawBytes[index]
                        if (bytes.isEmpty()) continue
                        val key = computeDispatchKey(bytes, hasCollisions)
                        val hash = ((key * multiplier + bytes.size) shr shift) and tableMask
                        if (dispatch[hash] == EMPTY_SLOT) {
                            dispatch[hash] = index
                        } else {
                            collision = true
                            break
                        }
                    }
                    if (!collision) {
                        return HashResult(
                            PerfectHashConfig(
                                shift,
                                multiplier,
                                tableSize,
                                extendedKeyHash = hasCollisions
                            ),
                            dispatch,
                        )
                    }
                }
            }
        }
        return null
    }

    private fun detectPrefixLengthCollisions(rawBytes: List<ByteArray>): Boolean {
        val seen = HashSet<Long>()
        for (bytes in rawBytes) {
            if (bytes.isEmpty()) continue
            var mask = 0L
            if (bytes.isNotEmpty()) mask = mask or (bytes[0].toLong() and BYTE_MASK_LONG)
            if (bytes.size >= 2) mask = mask or ((bytes[1].toLong() and BYTE_MASK_LONG) shl SHIFT_8)
            if (bytes.size >= 3) mask = mask or ((bytes[2].toLong() and BYTE_MASK_LONG) shl SHIFT_16)
            if (bytes.size >= 4) mask = mask or ((bytes[3].toLong() and BYTE_MASK_LONG) shl SHIFT_24)
            val packed = mask or (bytes.size.toLong() shl SHIFT_32)
            if (!seen.add(packed)) return true
        }
        return false
    }

    private fun computeDispatchKey(bytes: ByteArray, hasCollisions: Boolean): Int {
        var key = 0
        if (bytes.isNotEmpty()) key = key or (bytes[0].toInt() and BYTE_MASK)
        if (bytes.size >= 2) key = key or ((bytes[1].toInt() and BYTE_MASK) shl SHIFT_8)
        if (bytes.size >= 3) key = key or ((bytes[2].toInt() and BYTE_MASK) shl SHIFT_16)
        if (bytes.size >= 4) key = key or ((bytes[3].toInt() and BYTE_MASK) shl SHIFT_24)
        if (hasCollisions) {
            var ci = 4
            while (ci < bytes.size) {
                key = key * COLLISION_HASH_MULTIPLIER + (bytes[ci].toInt() and BYTE_MASK)
                ci++
            }
        }
        return key
    }
}
