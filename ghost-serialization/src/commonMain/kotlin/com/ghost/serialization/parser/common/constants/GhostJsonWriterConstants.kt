package com.ghost.serialization.parser.common.constants

import okio.ByteString.Companion.encodeUtf8

/** Fast-path writer literals, buffer sizing, and UTF-8 packing constants. */
object GhostJsonWriterConstants {

    // --- Pre-encoded ByteStrings (Fast-Path Writing) ---
    @PublishedApi
    internal val TRUE_BS = "true".encodeUtf8()

    @PublishedApi
    internal val FALSE_BS = "false".encodeUtf8()

    @PublishedApi
    internal val NULL_BS = "null".encodeUtf8()

    @PublishedApi
    internal val MIN_INT_BS = "-2147483648".encodeUtf8()

    @PublishedApi
    internal val MIN_LONG_BS = "-9223372036854775808".encodeUtf8()

    @PublishedApi
    internal val DOT_ZERO = ".0".encodeToByteArray()

    @PublishedApi
    internal val COLON_QUOTE_BS = "\":".encodeUtf8()

    @PublishedApi
    internal val TYPE_BS = "type".encodeUtf8()

    // --- Buffer Sizes & Scaling ---
    /** Minimum scratch buffer size for small objects. */
    const val TIER_SMALL_INT = 1024

    /** Scaling factor when growing internal buffers. */
    const val BUFFER_SCALE_FACTOR = 2

    /** Shift factor when growing buffer capacity by 1.5. */
    const val CAPACITY_GROWTH_SHIFT = 1

    /** Size of the scratch buffer for numeric itoa/dtoa. */
    const val LONG_SCRATCH_SIZE = 24

    /** Size of the hot-path writer scratch buffer. */
    const val WRITER_SCRATCH_SIZE = 512

    /**
     * Initial capacity of `GhostJsonStringReader.slowPathChars`. Grows on demand; kept as a
     * constant (not a heuristic) since escape decode is rare and this only amortizes the first few.
     */
    const val STRING_ESCAPE_SCRATCH_SIZE = 256

    const val INITIAL_WRITE_BUFFER_SIZE = 8 * 1024

    /**
     * Bytes copied from the Okio buffer per [com.ghost.serialization.parser.streaming.StreamingGhostSource]
     * slow-path refill (also the sliding-consume retain margin, see `.releaseBefore`).
     * Kept at one Okio segment (8 KB): larger windows didn't improve throughput and raised
     * both allocated KB/op and peak retained Okio prefix.
     */
    const val STREAMING_BUFFER_SIZE = 8192

    /**
     * Max string length for the plain-ASCII writeQuotedAscii fast-path (longer strings fall
     * through to the scratch-buffer escape path). Larger than the scratch buffer so every
     * short JSON string benefits.
     */
    const val PLAIN_ASCII_FAST_PATH_LIMIT = 512

    const val SCRATCH_BUFFER_SIZE = 48
    const val BASE_TEN = 10
    const val BASE_HUNDRED = 100
    const val ASCII_OFFSET = 48

    /** Bytes consumed by the opening + closing quotes around a JSON string value. */
    const val STRING_QUOTE_PAIR_BYTES = 2

    /** Stores two ASCII bytes per number (00-99) for fast numeric formatting. */
    val DOUBLE_DIGIT_LUT = ByteArray(size = 200) { i ->
        val num = i / 2
        if (i % 2 == 0) (num / 10 + '0'.code).toByte() else (num % 10 + '0'.code).toByte()
    }

    /** Stores two ASCII characters per number (00-99) for fast numeric formatting. */
    val DOUBLE_DIGIT_LUT_CHARS = CharArray(size = 200) { i ->
        val num = i / 2
        if (i % 2 == 0) (num / 10 + '0'.code).toChar() else (num % 10 + '0'.code).toChar()
    }

    /** Bitmask for ASCII characters 0-63 that require escaping (Controls + Quote). */
    const val NEEDS_ESCAPE_MASK_LOW = 0x4FFFFFFFFL

    /** Bitmask for ASCII characters 64-127 that require escaping (Backslash). */
    const val NEEDS_ESCAPE_MASK_HIGH = 0x10000000L

    /** Combined masks for branch-free lookup. */
    val ESCAPE_MASKS = longArrayOf(NEEDS_ESCAPE_MASK_LOW, NEEDS_ESCAPE_MASK_HIGH)

    /** Pre-encoded replacement bytes for common escape sequences (e.g., \n -> ['\\', 'n']). */
    val ESCAPE_REPLACEMENTS = arrayOfNulls<ByteArray>(size = 128).apply {
        this['"'.code] = "\\\"".encodeToByteArray()
        this['\\'.code] = "\\\\".encodeToByteArray()
        this['\n'.code] = "\\n".encodeToByteArray()
        this['\r'.code] = "\\r".encodeToByteArray()
        this['\t'.code] = "\\t".encodeToByteArray()
        this['\b'.code] = "\\b".encodeToByteArray()
        this[''.code] = "\\f".encodeToByteArray()
    }

    // --- UTF-8 Encoding ---
    /** Code point upper bound (exclusive) for the 1-byte UTF-8 form (i.e. ASCII). */
    const val UTF8_1BYTE_LIMIT = 0x80
    const val UTF8_1BYTE_MAX = 0x7F

    /** Code point upper bound (exclusive) for the 2-byte UTF-8 form. */
    const val UTF8_2BYTE_LIMIT = 0x800
    const val UTF8_2BYTE_MAX = 0x7FF

    /** Leading-byte prefix for a 2-byte UTF-8 sequence (110xxxxx). */
    const val UTF8_2BYTE_PREFIX = 0xC0

    /** Leading-byte prefix for a 3-byte UTF-8 sequence (1110xxxx). */
    const val UTF8_3BYTE_PREFIX = 0xE0

    /** Leading-byte prefix for a 4-byte UTF-8 sequence (11110xxx). */
    const val UTF8_4BYTE_PREFIX = 0xF0

    /** Continuation-byte prefix for trailing UTF-8 bytes (10xxxxxx). */
    const val UTF8_CONT_PREFIX = 0x80

    /** Six-bit mask used to extract the payload of a UTF-8 continuation byte. */
    const val UTF8_CONT_MASK = 0x3F

    /** Right-shift by 6 bits when packing UTF-8 continuation bytes. */
    const val UTF8_SHIFT_6 = 6

    /** Right-shift by 12 bits when packing the middle byte of a 3-byte UTF-8 sequence. */
    const val UTF8_SHIFT_12 = 12

    /** Right-shift by 18 bits when packing the leading byte of a 4-byte UTF-8 sequence. */
    const val UTF8_SHIFT_18 = 18

    /** `byte shr 5` tag for a 2-byte UTF-8 lead (`110xxxxx` → tag `0x6`). */
    const val UTF8_2BYTE_LEAD_SHIFT = 5
    const val UTF8_2BYTE_LEAD_TAG = 0x6

    /** `byte shr 4` tag for a 3-byte UTF-8 lead (`1110xxxx` → tag `0xE`). */
    const val UTF8_3BYTE_LEAD_SHIFT = 4
    const val UTF8_3BYTE_LEAD_TAG = 0xE

    /** Payload bit mask for a 2-byte UTF-8 lead byte (`xxxxx` in `110xxxxx`). */
    const val UTF8_2BYTE_PAYLOAD_MASK = 0x1F

    /** Payload bit mask for a 3-byte UTF-8 lead byte (`xxxx` in `1110xxxx`). */
    const val UTF8_3BYTE_PAYLOAD_MASK = 0x0F

    /** Payload bit mask for a 4-byte UTF-8 lead byte (`xxx` in `11110xxx`). */
    const val UTF8_4BYTE_PAYLOAD_MASK = 0x07

    /** Low 10 bits of a supplementary-plane offset when forming a UTF-16 surrogate pair. */
    const val SURROGATE_PAIR_MASK = 0x3FF

    /** Worst-case number of UTF-8 bytes for any BMP code point (3 + 1 trailing surrogate). */
    const val UTF8_MAX_BMP_BYTES = 4

    // --- UTF-8 sequence sizes (char → byte width); used by charToBytePosition / byteToCharPosition ---
    /** Width in bytes of a 1-byte (ASCII) UTF-8 code point. */
    const val UTF8_1BYTE_SIZE = 1

    /** Width in bytes of a 2-byte UTF-8 code point (U+0080..U+07FF). */
    const val UTF8_2BYTE_SIZE = 2

    /** Width in bytes of a 3-byte UTF-8 code point (U+0800..U+FFFF, excluding surrogates). */
    const val UTF8_3BYTE_SIZE = 3

    /** Width in bytes of a 4-byte UTF-8 code point (U+10000..U+10FFFF, surrogate pair in Kotlin String). */
    const val UTF8_4BYTE_SIZE = 4

    /** UTF-8 encoding of the Unicode replacement character `U+FFFD`. */
    val UTF8_REPLACEMENT_CHAR: ByteArray = byteArrayOf(
        0xEF.toByte(),
        0xBF.toByte(),
        0xBD.toByte()
    )
}