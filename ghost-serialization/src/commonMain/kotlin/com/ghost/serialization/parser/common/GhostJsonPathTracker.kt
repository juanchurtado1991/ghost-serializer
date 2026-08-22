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
 */
@PublishedApi
internal class GhostJsonPathTracker(initialCapacity: Int = 16) {
    private var kinds: ByteArray = ByteArray(initialCapacity)
    private var keys: Array<String?> = arrayOfNulls(initialCapacity)
    private var indices: IntArray = IntArray(initialCapacity)
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

    fun pushKey(name: String) {
        ensureCapacity()
        kinds[size] = KIND_KEY
        keys[size] = name
        indices[size] = 0
        size++
    }

    fun pushObject() {
        ensureCapacity()
        kinds[size] = KIND_OBJECT
        keys[size] = null
        indices[size] = 0
        size++
    }

    fun pushArray() {
        ensureCapacity()
        kinds[size] = KIND_ARRAY
        keys[size] = null
        indices[size] = -1
        size++
    }

    /** Call when [hasNext] returns true inside an array, or before each [readList] element. */
    @PublishedApi
    internal fun enterArrayElement() {
        if (size == 0 || kinds[size - 1] != KIND_ARRAY) return
        val i = size - 1
        if (indices[i] < 0) {
            indices[i] = 0
        } else {
            indices[i]++
        }
    }

    fun isInArray(): Boolean = size > 0 && kinds[size - 1] == KIND_ARRAY

    /**
     * After a scalar value under an object key: pop the key.
     * Scalar elements inside an array leave the array frame in place.
     */
    @PublishedApi
    internal fun finishScalarValue() {
        if (size > 0 && kinds[size - 1] == KIND_KEY) {
            size--
        }
    }

    /**
     * After [endObject]: pop the `{` frame, then the owning key if this object was a field value.
     */
    fun finishObjectValue() {
        if (size > 0 && kinds[size - 1] == KIND_OBJECT) {
            size--
        }
        if (size > 0 && kinds[size - 1] == KIND_KEY) {
            size--
        }
    }

    /**
     * After [endArray]: pop the array frame, then the owning key if this array was a field value.
     */
    @PublishedApi
    internal fun finishArrayValue() {
        if (size > 0 && kinds[size - 1] == KIND_ARRAY) {
            size--
        }
        if (size > 0 && kinds[size - 1] == KIND_KEY) {
            size--
        }
    }

    fun formatPath(): String {
        if (size == 0) return ROOT
        val sb = StringBuilder(ROOT)
        for (i in 0 until size) {
            when (kinds[i]) {
                KIND_KEY -> {
                    val name = keys[i] ?: continue
                    if (isSimpleName(name)) {
                        sb.append('.').append(name)
                    } else {
                        sb.append("['").append(name).append("']")
                    }
                }
                KIND_ARRAY -> {
                    val index = indices[i]
                    if (index >= 0) {
                        sb.append('[').append(index).append(']')
                    }
                }
                // KIND_OBJECT: no path segment
            }
        }
        return sb.toString()
    }

    private fun ensureCapacity() {
        if (size < kinds.size) return
        val newSize = kinds.size * 2
        kinds = kinds.copyOf(newSize)
        keys = keys.copyOf(newSize)
        indices = indices.copyOf(newSize)
    }

    private companion object {
        const val KIND_KEY: Byte = 0
        const val KIND_ARRAY: Byte = 1
        const val KIND_OBJECT: Byte = 2
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
