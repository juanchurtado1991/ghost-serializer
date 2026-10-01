package com.ghost.serialization.types

/**
 * Opaque JSON held as verbatim UTF-8 bytes of the wire representation, captured zero-copy from a
 * [ByteArray] source without building an intermediate parse tree. Prefer over [ByteArray] on
 * model fields: it documents intent and provides value-based [equals]/[hashCode].
 *
 * When captured from a flat byte reader, [storage], [storageOffset], and [storageLength] alias
 * the parse input buffer until [bytes] is accessed, which materializes an exact-length copy.
 *
 * [contentHashCode] uses the same seed and multiplier as `kotlin.collections.contentHashCode`
 * and `java.util.Arrays.hashCode`, so it matches [String.hashCode] for the same bytes.
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
        get() = if (isFullStorageSpan()) {
            storage
        } else {
            storage.copyOfRange(
                fromIndex = storageOffset,
                toIndex = storageOffset + storageLength
            )
        }

    /** Decodes the captured UTF-8 JSON bytes as a [String] (wire form, including quotes for strings). */
    fun decodeToString(): String = storage.decodeToString(
        startIndex = storageOffset,
        endIndex = storageOffset + storageLength
    )

    val endExclusive: Int
        get() = storageOffset + storageLength

    /** Classifies the JSON value without parsing or copying the payload. */
    fun kind(): RawJsonKind = RawJsonValueScanner.kind(raw = this)

    val isJsonNull: Boolean
        get() = RawJsonValueScanner.isJsonNull(raw = this)

    /** `true`/`false` for JSON booleans; `null` for `null`, non-boolean, or invalid payloads. */
    fun asBooleanOrNull(): Boolean? = RawJsonValueScanner.asBooleanOrNull(raw = this)

    /**
     * Human-readable scalar for UI (capability status, labels). Strings are unquoted;
     * numbers/booleans/null use wire text; objects/arrays return full JSON text.
     */
    fun asDisplayString(): String = RawJsonValueScanner.asDisplayString(raw = this)

    /** JSON number as [Double]; integer path avoids extra allocation; fraction/exponent uses UTF-8 decode once. */
    fun asDoubleOrNull(): Double? = RawJsonValueScanner.asDoubleOrNull(raw = this)

    /** JSON integer when the payload is a number without fraction or exponent; otherwise `null`. */
    fun asIntOrNull(): Int? = RawJsonValueScanner.asIntOrNull(raw = this)

    /** JSON integer when the payload is a number without fraction or exponent; otherwise `null`. */
    fun asLongOrNull(): Long? = RawJsonValueScanner.asLongOrNull(raw = this)

    /**
     * Decoded string contents when the payload is a JSON string (`"..."`); otherwise `null`.
     * ASCII fast path avoids escape scanning allocations when no `\` is present.
     */
    fun asStringOrNull(): String? = RawJsonValueScanner.asStringOrNull(raw = this)

    fun contentEquals(
        other: RawJson?
    ): Boolean {
        if (other == null) return false
        if (storageLength != other.storageLength) return false
        if (isFullStorageSpan() && other.isFullStorageSpan()) {
            return storage.contentEquals(other = other.storage)
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

    override fun equals(
        other: Any?
    ): Boolean {
        if (this === other) return true
        if (other !is RawJson) return false
        return contentEquals(other = other)
    }

    override fun hashCode(): Int = contentHashCode()

    override fun toString(): String = "RawJson(${decodeToString()})"

    companion object {
        private const val CONTENT_HASH_SEED = 1

        private const val CONTENT_HASH_MULTIPLIER = 31

        /** Wraps a slice of an existing buffer without
         *  copying (flat-reader capture path). */
        fun fromBufferSlice(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): RawJson = RawJson(
            storage = buffer,
            storageOffset = offset,
            storageLength = length
        )

        /** Encodes [json] to UTF-8 bytes. Prefer [fromBufferSlice]
         * or reader capture when decoding parsed wire bytes. */
        fun fromString(
            json: String
        ): RawJson = fromUtf8Bytes(bytes = json.encodeToByteArray())

        fun fromUtf8Bytes(
            bytes: ByteArray
        ): RawJson = RawJson(
            storage = bytes,
            storageOffset = 0,
            storageLength = bytes.size
        )
    }
}
