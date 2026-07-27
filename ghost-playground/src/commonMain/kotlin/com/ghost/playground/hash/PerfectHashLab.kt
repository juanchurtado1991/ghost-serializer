package com.ghost.playground.hash

/**
 * Perfect-hash search aligned with ghost-compiler PerfectHashFinder.
 * Lives in the playground so Wasm can build dispatch tables without the JVM KSP module.
 */
data class PerfectHashConfig(
    val shift: Int,
    val multiplier: Int,
    val tableSize: Int,
    val extendedKeyHash: Boolean,
)

data class DispatchSlot(
    val index: Int,
    val fieldName: String?,
    val occupied: Boolean,
)

object PerfectHashLab {
    private const val HASH_MULTIPLIER_START = 31
    private const val HASH_MULTIPLIER_LIMIT = 15_000
    private const val HASH_MULTIPLIER_STEP = 2
    private const val HASH_SHIFT_LIMIT = 16
    private const val COLLISION_HASH_MULTIPLIER = 31
    private const val BYTE_MASK = 0xFF
    private const val BYTE_MASK_LONG = 0xFFL
    private const val EMPTY_TABLE_SIZE = 128
    private val TABLE_SIZES = intArrayOf(128, 256, 512, 1024, 2048, 4096, 8192)

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

    /** Returns config + dispatch indices (-1 = empty slot). */
    fun dispatchTable(names: List<String>): Pair<PerfectHashConfig, IntArray> {
        if (names.isEmpty()) {
            return PerfectHashConfig(
                0,
                HASH_MULTIPLIER_START,
                EMPTY_TABLE_SIZE,
                extendedKeyHash = false
            ) to
                    IntArray(EMPTY_TABLE_SIZE) { -1 }
        }
        findInternal(names, useExtendedKeyHash = false)?.let { return it.config to it.dispatch }
        findInternal(names, useExtendedKeyHash = true)?.let { return it.config to it.dispatch }
        error("Could not find a collision-free perfect hash for fields: ${names.joinToString()}")
    }

    /**
     * All slots + a human-readable hash summary, for the dispatch preview UI. Truncating this
     * (a prior version capped at 64) is wrong: the minimum table size is 128 and the hash spreads
     * fields across the full range, so a field landing in slot 64-127 would silently vanish from
     * the preview even though it's really dispatched — looked like a missing-field bug.
     */
    fun dispatchPreview(names: List<String>): Pair<List<DispatchSlot>, String> {
        val (cfg, table) = dispatchTable(names)
        val slots = List(cfg.tableSize) { slotIndex ->
            val fieldIndex = table.getOrNull(slotIndex) ?: -1
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
            result = 31 * result + dispatch.contentHashCode()
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
                    val dispatch = IntArray(tableSize) { -1 }
                    var collision = false
                    for (index in rawBytes.indices) {
                        val bytes = rawBytes[index]
                        if (bytes.isEmpty()) continue
                        val key = computeDispatchKey(bytes, hasCollisions)
                        val hash = ((key * multiplier + bytes.size) shr shift) and tableMask
                        if (dispatch[hash] == -1) {
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
            if (bytes.size >= 2) mask = mask or ((bytes[1].toLong() and BYTE_MASK_LONG) shl 8)
            if (bytes.size >= 3) mask = mask or ((bytes[2].toLong() and BYTE_MASK_LONG) shl 16)
            if (bytes.size >= 4) mask = mask or ((bytes[3].toLong() and BYTE_MASK_LONG) shl 24)
            val packed = mask or (bytes.size.toLong() shl 32)
            if (!seen.add(packed)) return true
        }
        return false
    }

    private fun computeDispatchKey(bytes: ByteArray, hasCollisions: Boolean): Int {
        var key = 0
        if (bytes.isNotEmpty()) key = key or (bytes[0].toInt() and BYTE_MASK)
        if (bytes.size >= 2) key = key or ((bytes[1].toInt() and BYTE_MASK) shl 8)
        if (bytes.size >= 3) key = key or ((bytes[2].toInt() and BYTE_MASK) shl 16)
        if (bytes.size >= 4) key = key or ((bytes[3].toInt() and BYTE_MASK) shl 24)
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
