package com.ghost.serialization.parser.common

/**
 * Lightweight JSONPath breadcrumb stack for parse errors.
 *
 * Happy path: push/pop of ints + [String] refs only — no [StringBuilder] until [formatPath]
 * is called from [com.ghost.serialization.exception.GhostJsonException] construction.
 *
 * Segment kinds:
 * - **Key** — object field name (after a successful select / [nextKey])
 * - **Object** — anonymous `{` frame (so nested [endObject] does not pop a parent key)
 * - **Array** — element index (`-1` = before first element; [enterArrayElement] advances)
 *
 * Each stack frame packs its kind and (for array frames) its element index into one `Int`
 * (`slots[i]`) instead of two parallel arrays — bits 0-1 are the kind tag, bits 2-31 are the
 * array index biased by +1 (so the `-1` "before first element" sentinel maps to `0` and needs
 * no sign bit). `keys` stays a separate array since it holds references.
 */
@PublishedApi
internal class GhostJsonPathTracker(initialCapacity: Int = 16) {
    private var slots: IntArray = IntArray(initialCapacity)
    private var keys: Array<String?> = arrayOfNulls(initialCapacity)
    private var size: Int = 0

    @PublishedApi
    internal fun reset() {
        size = 0
    }

    @PublishedApi
    internal fun mark(): Int = size

    @PublishedApi
    internal fun resetTo(mark: Int) {
        size = if (mark < 0) 0 else mark.coerceAtMost(size)
    }

    // pushObject/pushArray don't write `keys[size]`, and pushKey/pushObject leave the index
    // bits of `slots[size]` at zero — formatPath() only ever reads `keys[i]` under KIND_KEY and
    // the index bits under KIND_ARRAY, so a stale value left over from a shallower frame at the
    // same stack depth is never observed; only the kind tag needs to be current for every push.

    fun pushKey(name: String) {
        ensureCapacity()
        slots[size] = KIND_KEY
        keys[size] = name
        size++
    }

    fun pushObject() {
        ensureCapacity()
        slots[size] = KIND_OBJECT
        size++
    }

    fun pushArray() {
        ensureCapacity()
        slots[size] = KIND_ARRAY // biased index 0 == actual index -1
        size++
    }

    /** Call when [hasNext] returns true inside an array, or before each [readList] element. */
    @PublishedApi
    internal fun enterArrayElement() {
        if (size == 0) return
        val i = size - 1
        val slot = slots[i]
        if (slot and KIND_MASK != KIND_ARRAY) return
        // ushr (not shr): the biased index is logically unsigned and its top bit sets once it
        // approaches MAX_BIASED_INDEX, which would sign-extend under an arithmetic shift.
        val biased = slot ushr INDEX_SHIFT
        if (biased < MAX_BIASED_INDEX) {
            slots[i] = slot + INDEX_UNIT
        }
        // else: clamp — stop advancing rather than wrap; formatPath renders the capped index.
    }

    /** After a scalar under an object key: pop the key. Array elements leave the array frame. */
    @PublishedApi
    internal fun finishScalarValue() {
        if (size > 0 && (slots[size - 1] and KIND_MASK) == KIND_KEY) {
            size--
        }
    }

    /**
     * After [endObject]: pop the `{` frame, then the owning key if this object was a field value.
     */
    fun finishObjectValue() {
        if (size > 0 && (slots[size - 1] and KIND_MASK) == KIND_OBJECT) {
            size--
        }
        if (size > 0 && (slots[size - 1] and KIND_MASK) == KIND_KEY) {
            size--
        }
    }

    /**
     * After [endArray]: pop the array frame, then the owning key if this array was a field value.
     */
    @PublishedApi
    internal fun finishArrayValue() {
        if (size > 0 && (slots[size - 1] and KIND_MASK) == KIND_ARRAY) {
            size--
        }
        if (size > 0 && (slots[size - 1] and KIND_MASK) == KIND_KEY) {
            size--
        }
    }

    fun formatPath(): String {
        if (size == 0) return ROOT
        val sb = StringBuilder(ROOT)
        for (i in 0 until size) {
            val slot = slots[i]
            when (slot and KIND_MASK) {
                KIND_KEY -> {
                    val name = keys[i] ?: continue
                    if (isSimpleName(name)) {
                        sb.append('.').append(name)
                    } else {
                        sb.append("['").append(name).append("']")
                    }
                }
                KIND_ARRAY -> {
                    val biased = slot ushr INDEX_SHIFT
                    if (biased > 0) {
                        sb.append('[').append(biased - INDEX_BIAS).append(']')
                    }
                }
                // KIND_OBJECT: no path segment
            }
        }
        return sb.toString()
    }

    private fun ensureCapacity() {
        if (size < slots.size) return
        val newSize = slots.size * 2
        slots = slots.copyOf(newSize)
        keys = keys.copyOf(newSize)
    }

    private companion object {
        const val KIND_MASK = 0b11
        const val INDEX_SHIFT = 2
        const val INDEX_UNIT = 1 shl INDEX_SHIFT
        const val INDEX_BIAS = 1
        const val KIND_KEY = 0
        const val KIND_ARRAY = 1
        const val KIND_OBJECT = 2
        const val MAX_BIASED_INDEX = (1 shl (Int.SIZE_BITS - INDEX_SHIFT)) - 1
        const val ROOT = "$"

        fun isSimpleName(name: String): Boolean {
            if (name.isEmpty()) return false
            for (i in name.indices) {
                val c = name[i]
                val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '$'
                if (!ok) return false
            }
            return true
        }
    }
}
