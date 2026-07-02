package com.ghost.serialization.types

/**
 * Opaque JSON held as verbatim UTF-8 bytes of the wire representation.
 *
 * Ghost serializes and deserializes [RawJson] as an inline JSON value (object, array,
 * string, number, boolean, or null) using zero-copy capture when parsed from a
 * [ByteArray] source, without building an intermediate parse tree.
 *
 * Prefer [RawJson] over [kotlin.ByteArray] on public model fields: it documents intent
 * and provides value-based [equals] / [hashCode].
 *
 * When captured from a flat byte reader, [storage], [storageOffset], and [storageLength]
 * alias the parse input buffer until [bytes] is accessed (which materializes an
 * exact-length copy for slice values).
 */
class RawJson internal constructor(
    val storage: ByteArray,
    val storageOffset: Int,
    val storageLength: Int
) {

    /**
     * Exact-length UTF-8 payload. Materializes a copy when this value is a slice into
     * a larger [storage] buffer.
     */
    val bytes: ByteArray
        get() = if (storageOffset == 0 && storageLength == storage.size) {
            storage
        } else {
            storage.copyOfRange(storageOffset, storageOffset + storageLength)
        }

    /** Decodes the captured UTF-8 JSON bytes as a [String]. */
    fun decodeToString(): String =
        storage.decodeToString(storageOffset, storageOffset + storageLength)

    /** Value-based equality for the underlying JSON bytes. */
    fun contentEquals(other: RawJson?): Boolean {
        if (other == null) return false
        if (storageLength != other.storageLength) return false
        if (isFullStorageSpan() && other.isFullStorageSpan()) {
            return storage.contentEquals(other.storage)
        }
        val end = storageOffset + storageLength
        var otherIndex = other.storageOffset
        for (index in storageOffset until end) {
            if (storage[index] != other.storage[otherIndex++]) {
                return false
            }
        }
        return true
    }

    /** Value-based hash for the underlying JSON bytes. */
    fun contentHashCode(): Int {
        if (isFullStorageSpan()) {
            return storage.contentHashCode()
        }
        var result = CONTENT_HASH_SEED
        val end = storageOffset + storageLength
        for (index in storageOffset until end) {
            result = CONTENT_HASH_MULTIPLIER * result + storage[index]
        }
        return result
    }

    private fun isFullStorageSpan(): Boolean =
        storageOffset == 0 && storageLength == storage.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawJson) return false
        return contentEquals(other)
    }

    override fun hashCode(): Int = contentHashCode()

    override fun toString(): String = "RawJson(${decodeToString()})"

    companion object {
        /** Initial accumulator for [contentHashCode]; matches [ByteArray.contentHashCode]. */
        private const val CONTENT_HASH_SEED = 1

        /** Multiplier for [contentHashCode]; matches `java.util.Arrays.hashCode` and [String.hashCode]. */
        private const val CONTENT_HASH_MULTIPLIER = 31

        /** Wraps an owned UTF-8 buffer exactly as it appears in JSON. */
        fun fromUtf8Bytes(bytes: ByteArray): RawJson = RawJson(bytes, 0, bytes.size)

        /** Wraps a slice of an existing buffer without copying (flat-reader capture path). */
        fun fromBufferSlice(buffer: ByteArray, offset: Int, length: Int): RawJson =
            RawJson(buffer, offset, length)

        /** Encodes [json] to UTF-8 bytes. For round-trip tests, prefer wire capture. */
        fun fromString(json: String): RawJson = fromUtf8Bytes(json.encodeToByteArray())
    }
}
