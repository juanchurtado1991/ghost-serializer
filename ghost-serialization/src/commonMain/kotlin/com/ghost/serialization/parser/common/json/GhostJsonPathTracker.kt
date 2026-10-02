package com.ghost.serialization.parser.common.json

/**
 * Lightweight JSONPath breadcrumb stack for parse errors.
 *
 * Happy path: push/pop of ints + [String] refs only — no [StringBuilder] until [formatPath]
 * is called from [com.ghost.serialization.exception.GhostJsonException] construction.
 *
 * Segment kinds:
 * - **Key** — object field name, pushed by [pushKey] after a successful field select.
 * - **Object** — anonymous `{` frame, pushed by [pushObject] so a nested [finishObjectValue]
 *   does not pop a parent key.
 * - **Array** — element index (`-1` = before first element), pushed by [pushArray] and advanced
 *   by [enterArrayElement] before each element is read.
 *
 * Each stack frame packs its kind and (for array frames) its element index into one `Int`
 * (`slots[i]`) instead of two parallel arrays — bits 0-1 are the kind tag, bits 2-31 are the
 * array index biased by +1 (so the `-1` "before first element" sentinel maps to `0` and needs
 * no sign bit). `keys` stays a separate array since it holds references.
 *
 * [pushObject]/[pushArray] don't write `keys[size]`, and [pushKey]/[pushObject] leave the index
 * bits of `slots[size]` at zero — [formatPath] only ever reads `keys[i]` under a key frame and
 * the index bits under an array frame, so a stale value left over from a shallower frame at the
 * same stack depth is never observed; only the kind tag needs to be current for every push.
 *
 * Frame lifecycle after a value is fully read: [finishScalarValue] pops the owning key (if any)
 * after a scalar; [finishObjectValue] pops the `{` frame then the owning key; [finishArrayValue]
 * pops the array frame then the owning key.
 */
@PublishedApi
internal class GhostJsonPathTracker(initialCapacity: Int = 16) {
    private var slots: IntArray = IntArray(initialCapacity)
    private var keys: Array<String?> = arrayOfNulls(initialCapacity)
    private var size: Int = 0

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

    @PublishedApi
    internal fun finishArrayValue() {
        popFrameIfKind(kind = KIND_ARRAY)
        popFrameIfKind(kind = KIND_KEY)
    }

    fun finishObjectValue() {
        popFrameIfKind(kind = KIND_OBJECT)
        popFrameIfKind(kind = KIND_KEY)
    }

    @PublishedApi
    internal fun finishScalarValue() {
        popFrameIfKind(kind = KIND_KEY)
    }

    fun formatPath(): String {
        if (size == 0) return ROOT
        val sb = StringBuilder(ROOT)
        for (i in 0 until size) {
            val slot = slots[i]
            when (slot and KIND_MASK) {
                KIND_KEY -> {
                    val name = keys[i] ?: continue
                    if (isSimpleName(name = name)) {
                        sb.append(KEY_DOT).append(name)
                    } else {
                        sb.append(KEY_BRACKET_OPEN).append(name).append(KEY_BRACKET_CLOSE)
                    }
                }
                KIND_ARRAY -> {
                    val biased = slot ushr INDEX_SHIFT
                    if (biased > 0) {
                        sb.append(INDEX_BRACKET_OPEN).append(biased - INDEX_BIAS).append(INDEX_BRACKET_CLOSE)
                    }
                }
                // KIND_OBJECT: no path segment
            }
        }
        return sb.toString()
    }

    @PublishedApi
    internal fun mark(): Int = size

    fun pushArray() {
        ensureCapacity()
        slots[size] = KIND_ARRAY // biased index 0 == actual index -1
        size++
    }

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

    @PublishedApi
    internal fun reset() {
        size = 0
    }

    @PublishedApi
    internal fun resetTo(mark: Int) {
        size = if (mark < 0) 0 else mark.coerceAtMost(size)
    }

    private fun ensureCapacity() {
        if (size < slots.size) return
        val newSize = slots.size * 2
        slots = slots.copyOf(newSize)
        keys = keys.copyOf(newSize)
    }

    /** Pops the top frame if it matches [kind]; no-op on an empty stack or a kind mismatch. */
    private fun popFrameIfKind(kind: Int) {
        if (size > 0 && (slots[size - 1] and KIND_MASK) == kind) {
            size--
        }
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

        const val KEY_DOT = '.'
        const val KEY_BRACKET_OPEN = "['"
        const val KEY_BRACKET_CLOSE = "']"
        const val INDEX_BRACKET_OPEN = '['
        const val INDEX_BRACKET_CLOSE = ']'

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
