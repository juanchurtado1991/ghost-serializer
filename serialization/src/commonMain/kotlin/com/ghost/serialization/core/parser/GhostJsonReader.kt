package com.ghost.serialization.core.parser

import com.ghost.serialization.core.exception.GhostJsonException
import okio.BufferedSource


/**
 * High-performance JSON reader designed for Kotlin Multiplatform.
 *
 * GhostJsonReader operates directly on a contiguous [ByteArray] buffer to minimize
 * memory allocations and maximize throughput. It avoids reflection and intermediate
 * stream abstractions.
 *
 * @param data The raw JSON byte buffer to be processed.
 * @param maxDepth The maximum allowed recursion depth for nested structures.
 * @param strictMode If enabled, the reader throws an exception on unknown JSON fields.
 */
class GhostJsonReader(
    @PublishedApi internal var data: ByteArray,
    @PublishedApi
    internal val maxDepth: Int = 255,
    internal val strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false
) {
    fun reset(newData: ByteArray) {
        this.data = newData
        this.positon = 0
        this.depth = 0
        this.nextTokenByte = -1
    }

    /**
     * Releases the data buffer to prevent memory leaks in recycled scenarios.
     */
    fun clear() {
        this.data = byteArrayOf()
        this.positon = 0
        this.nextTokenByte = -1
    }

    constructor(
        source: BufferedSource,
        maxDepth: Int = 255,
        strictMode: Boolean = false
    ) : this(
        source.readByteArray(),
        maxDepth,
        strictMode
    )

    private var _position = 0

    @PublishedApi
    internal var positon: Int
        get() = _position
        set(value) {
            _position = value
            _nextTokenByte = -1
        }

    @PublishedApi
    internal var depth: Int = 0

    private var _nextTokenByte: Int = -1

    @PublishedApi
    internal var nextTokenByte: Int
        get() = _nextTokenByte
        set(value) {
            _nextTokenByte = value
        }

    val path: String get() = GhostJsonConstants.PATH_ROOT
    internal val stringPool = arrayOfNulls<String>(
        GhostJsonConstants.STR_POOL_SIZE
    )

    @PublishedApi
    internal fun internalSkip(number: Int) {
        positon += number
    }

    internal fun expectByte(expected: Byte) {
        if (positon >= data.size) {
            throwError("Expected '${expected.toInt().toChar()}' but reached end")
        }

        val actual = data[positon++]
        if (actual != expected) {
            throwError(
                "Expected '${expected.toInt().toChar()}' but found '${
                    actual.toInt().toChar()
                }'"
            )
        }
    }

    internal fun internalSelect(options: JsonReaderOptions): Int {
        val remaining = data.size - positon
        if (remaining <= 0) return -2

        var length = 0
        // Vectorized scan for the closing quote
        while (positon + length + 7 < data.size) {
            if (data[positon + length] == 34.toByte()) break
            if (data[positon + length + 1] == 34.toByte()) {
                length += 1; break
            }
            if (data[positon + length + 2] == 34.toByte()) {
                length += 2; break
            }
            if (data[positon + length + 3] == 34.toByte()) {
                length += 3; break
            }
            if (data[positon + length + 4] == 34.toByte()) {
                length += 4; break
            }
            if (data[positon + length + 5] == 34.toByte()) {
                length += 5; break
            }
            if (data[positon + length + 6] == 34.toByte()) {
                length += 6; break
            }
            if (data[positon + length + 7] == 34.toByte()) {
                length += 7; break
            }
            length += 8
        }

        while (positon + length < data.size && data[positon + length] != 34.toByte()) {
            length++
        }

        if (positon + length >= data.size) return -2

        // Multi-byte hash calculation
        var key = 0
        if (length >= 1) key = key or (data[positon].toInt() and 0xFF)
        if (length >= 2) key = key or ((data[positon + 1].toInt() and 0xFF) shl 8)
        if (length >= 3) key = key or ((data[positon + 2].toInt() and 0xFF) shl 16)
        if (length >= 4) key = key or ((data[positon + 3].toInt() and 0xFF) shl 24)

        val hashIndex = ((key * options.multiplier) + length) shr options.shift and 1023
        val hint = options.dispatch[hashIndex]

        if (hint != -1) {
            val optionBytes = options.rawBytes[hint]
            if (optionBytes.size == length) {
                var i = 0
                var match = true
                while (i + 3 < length) {
                    if (
                        data[positon + i] != optionBytes[i] || data[positon + i + 1] != optionBytes[i + 1] ||
                        data[positon + i + 2] != optionBytes[i + 2] || data[positon + i + 3] != optionBytes[i + 3]
                    ) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < length) {
                        if (data[positon + i] != optionBytes[i]) {
                            match = false; break
                        }
                        i++
                    }
                }
                if (match) {
                    internalSkip(length); return hint
                }
            }
        }

        // Collision recovery
        for (index in options.rawBytes.indices) {
            if (index == hint) continue
            val optionBytes = options.rawBytes[index]
            if (optionBytes.size == length) {
                var i = 0
                var match = true
                while (i + 3 < length) {
                    if (
                        data[positon + i] != optionBytes[i] || data[positon + i + 1] != optionBytes[i + 1] ||
                        data[positon + i + 2] != optionBytes[i + 2] || data[positon + i + 3] != optionBytes[i + 3]
                    ) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < length) {
                        if (data[positon + i] != optionBytes[i]) {
                            match = false; break
                        }
                        i++
                    }
                }
                if (match) {
                    internalSkip(length); return index
                }
            }
        }
        return -2
    }

    /**
     * Matches a quoted JSON string against the provided [options] without
     * consuming a following separator.
     *
     * This is primarily used for efficient Enum deserialization where the
     * value is matched directly against the hashing engine to avoid
     * intermediate string allocations and comparisons.
     *
     * @param options The predefined string options to match against.
     * @return The index of the matched string in [options], or -2 if no match is found.
     */
    fun selectString(options: JsonReaderOptions): Int {
        if (nextTokenByte == -1) skipWhitespace()
        if (positon >= data.size) return -1

        var currentByte = if (nextTokenByte != -1) {
            nextTokenByte
        } else {
            (data[positon].toInt() and 0xFF)
        }

        if (currentByte == 44) { // COMMA
            positon++; nextTokenByte = -1; skipWhitespace()
            if (positon >= data.size) return -1
            currentByte = data[positon].toInt() and 0xFF
        }

        if (currentByte == 125) return -1 // CLOSE_OBJ

        if (currentByte != 34) {
            throwError("Expected '\"' but found ${currentByte.toChar()}")
        }

        positon++; nextTokenByte = -1 // Skip opening quote

        val index = internalSelect(options)
        if (index >= 0) {
            if (positon < data.size && data[positon] == 34.toByte()) {
                positon++; nextTokenByte = -1
                return index
            }
            throwError("Expected '\"'")
        }

        // Cold path: Unknown field or enum value
        if (strictMode) {
            val name = readStringBody() // This skips closing quote
            throwError("${GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD}'$name'")
        }

        skipQuotedStringBody()
        return -2
    }

    /**
     * Matches the next JSON field name and consumes the key separator (':').
     *
     * This function implements fused field identification by performing token lookup,
     * whitespace skipping, and separator validation in a single pass to optimize
     * parser throughput.
     *
     * @param options The predefined field options to match against.
     * @return The index of the matched field in [options], -1 if the object is closed,
     *         or -2 if the field name is not recognized.
     */
    fun selectNameAndConsume(options: JsonReaderOptions): Int {
        if (nextTokenByte == -1) skipWhitespace()
        if (positon >= data.size) return -1

        var currentByte = if (nextTokenByte != -1) {
            nextTokenByte
        } else {
            (data[positon].toInt() and 0xFF)
        }

        if (currentByte == 44) { // COMMA
            positon++; nextTokenByte = -1; skipWhitespace()
            if (positon >= data.size) return -1
            currentByte = data[positon].toInt() and 0xFF
        }

        if (currentByte == 125) return -1 // CLOSE_OBJ

        if (currentByte != 34) throwError("Expected '\"' but found ${currentByte.toChar()}")
        positon++; nextTokenByte = -1 // Skip opening quote

        val index = internalSelect(options)
        if (index >= 0) {
            // Found it. Skip closing quote and separator
            if (positon < data.size && data[positon] == 34.toByte()) {
                positon++
                // Fused consumeKeySeparator
                skipWhitespace()
                if (positon < data.size && data[positon] == 58.toByte()) {
                    positon++; nextTokenByte = -1
                    skipWhitespace()
                    if (positon < data.size) nextTokenByte = data[positon].toInt() and 0xFF
                    return index
                }
                throwError("Expected ':'")
            }
            throwError("Expected '\"'")
        }

        // Cold path: Unknown field
        if (strictMode) {
            val name = readStringBody() // This skips closing quote
            throwError("${GhostJsonConstants.STRICT_MODE_UNKNOWN_FIELD}'$name'")
        }
        skipQuotedStringBody()
        return -2
    }

    /** Starts a JSON object '{'. Increases recursion depth. */
    fun beginObject() {
        checkDepth()
        skipWhitespace()
        expectByte(GhostJsonConstants.OPEN_OBJ); depth++
    }

    /** Ends a JSON object '}'. */
    fun endObject() {
        skipWhitespace()
        expectByte(GhostJsonConstants.CLOSE_OBJ)
        depth--
    }

    fun beginArray() {
        checkDepth()
        skipWhitespace()
        expectByte(GhostJsonConstants.OPEN_ARR)
        depth++
    }

    fun endArray() {
        skipWhitespace()
        expectByte(GhostJsonConstants.CLOSE_ARR)
        depth--
    }

    fun hasNext(): Boolean {
        val nextToken = peekNextToken()
        if (nextToken == -1) return false
        return nextToken.toByte() != GhostJsonConstants.CLOSE_OBJ && nextToken.toByte() != GhostJsonConstants.CLOSE_ARR
    }

    /** Reads a JSON string. Supports character escaping and automatic pooling. */
    fun nextString(): String {
        if (nextTokenByte == -1) skipWhitespace()
        return readQuotedString()
    }

    /** Reads a boolean value (true/false) without intermediate allocations. */
    fun nextBoolean(): Boolean {
        if (nextTokenByte == -1) skipWhitespace()
        if (positon + 4 > data.size) {
            throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
        }
        val currentByte = data[positon]
        if (currentByte == GhostJsonConstants.TRUE_CHAR) {
            if (data[positon + 1] == 'r'.code.toByte() &&
                data[positon + 2] == 'u'.code.toByte() &&
                data[positon + 3] == 'e'.code.toByte()
            ) {
                internalSkip(4); return true
            }
        } else if (currentByte == GhostJsonConstants.FALSE_CHAR) {
            if (positon + 5 > data.size) {
                throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
            }
            if (data[positon + 1] == 'a'.code.toByte() &&
                data[positon + 2] == 'l'.code.toByte() &&
                data[positon + 3] == 's'.code.toByte() &&
                data[positon + 4] == 'e'.code.toByte()
            ) {
                internalSkip(5); return false
            }
        }
        throwError("Expected boolean but found ${currentByte.toInt().toChar()}")
    }

    internal fun readQuotedString(): String {
        expectByte(GhostJsonConstants.QUOTE)
        return readStringBody()
    }

    internal fun readStringBody(): String {
        val start = positon
        // Ghost Vectorized Scan: Find quote or escape faster using Lookup Table
        while (positon + 3 < data.size) {
            val byte1 = data[positon].toInt() and 0xFF
            val byte2 = data[positon + 1].toInt() and 0xFF
            val byte3 = data[positon + 2].toInt() and 0xFF
            val byte4 = data[positon + 3].toInt() and 0xFF

            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte1]) break
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte2]) {
                positon += 1; break
            }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte3]) {
                positon += 2; break
            }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte4]) {
                positon += 3; break
            }
            positon += 4
        }

        while (positon < data.size) {
            val currentByte = data[positon]
            if (currentByte == GhostJsonConstants.QUOTE) {
                val length = positon - start
                return readPooledString(start, length)
            }
            if (currentByte.toInt() in 0..31) throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            if (currentByte == GhostJsonConstants.BACKSLASH) return readStringWithEscapes(start)
            positon++
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readPooledString(start: Int, length: Int): String {
        if (length <= 0) {
            internalSkip(1); return ""
        }
        if (length > GhostJsonConstants.MAX_POOL_STRING_LENGTH) {
            val result = data.decodeToString(start, start + length)
            internalSkip(1); return result
        }
        // Optimized Rolling Hash with loop unrolling
        var rollingHash = 0
        var i = start
        val end = start + length

        while (i + 3 < end) {
            rollingHash = (rollingHash shl 5) - rollingHash + (data[i].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (data[i + 1].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (data[i + 2].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (data[i + 3].toInt() and 0xFF)
            i += 4
        }
        while (i < end) {
            rollingHash = (rollingHash shl 5) - rollingHash + (data[i].toInt() and 0xFF)
            i++
        }

        val poolIndex = rollingHash and (GhostJsonConstants.STR_POOL_SIZE - 1)
        val cached = stringPool[poolIndex]

        if (cached != null && cached.length == length) {
            // Hot Path: Candidate found, verify bytes with unrolling
            var match = true
            if (length >= 4) {
                if (cached[0].code != (data[start].toInt() and 0xFF) ||
                    cached[1].code != (data[start + 1].toInt() and 0xFF) ||
                    cached[2].code != (data[start + 2].toInt() and 0xFF) ||
                    cached[3].code != (data[start + 3].toInt() and 0xFF)
                ) {
                    match = false
                }
            }
            if (match) {
                var j = if (length >= 4) 4 else 0
                while (j < length) {
                    if (cached[j].code != (data[start + j].toInt() and 0xFF)) {
                        match = false
                        break
                    }
                    j++
                }
                if (match) {
                    internalSkip(1) // Skip closing quote
                    return cached
                }
            }
        }

        // Cold Path: Decode and potentially update pool
        val result = data.decodeToString(start, start + length)
        stringPool[poolIndex] = result
        internalSkip(1) // Skip closing quote
        return result
    }

    private fun readStringWithEscapes(start: Int): String {
        val output = StringBuilder(GhostJsonConstants.STRING_BUILDER_CAPACITY)
        if (positon > start) {
            output.append(data.decodeToString(start, positon))
        }
        while (positon < data.size) {
            val currentByte = data[positon]
            if (currentByte == GhostJsonConstants.QUOTE) {
                internalSkip(1); return output.toString()
            }
            if (currentByte.toInt() in 0..31) {
                throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            }
            if (currentByte == GhostJsonConstants.BACKSLASH) {
                internalSkip(1)
                val escapedChar = readEscapeCode()
                if (escapedChar <= 0xFFFF) {
                    output.append(escapedChar.toChar())
                } else {
                    output.append(((escapedChar - 0x10000) shr 10 or 0xD800).toChar())
                    output.append(((escapedChar - 0x10000) and 0x3FF or 0xDC00).toChar())
                }
            } else {
                val scanStart = positon
                while (positon < data.size) {
                    val subByte = data[positon]
                    if (
                        subByte == GhostJsonConstants.QUOTE ||
                        subByte == GhostJsonConstants.BACKSLASH ||
                        subByte.toInt() in 0..31
                    ) {
                        break
                    }
                    internalSkip(1)
                }
                output.append(data.decodeToString(scanStart, positon))
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readEscapeCode(): Int {
        if (positon >= data.size) {
            throwError(GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR)
        }

        val escapeByte = data[positon]
        internalSkip(1)
        return when (escapeByte.toInt().toChar()) {
            'n' -> '\n'.code; 't' -> '\t'.code; 'r' -> '\r'.code
            'b' -> '\b'.code; 'f' -> '\u000C'.code
            'u' -> readUnicodeCode()
            '\\' -> '\\'.code; '"' -> '"'.code; '/' -> '/'.code
            else -> throwError("Invalid escape sequence: \\${escapeByte.toInt().toChar()}")
        }
    }

    private fun readUnicodeCode(): Int {
        if (positon + 4 > data.size) {
            throwError(GhostJsonConstants.UNTERMINATED_UNICODE_ERROR)
        }

        val hex = data.decodeToString(positon, positon + 4)
        internalSkip(4)
        val code = try {
            hex.toInt(16)
        } catch (_: Exception) {
            throwError("Invalid unicode escape: \\u$hex")
        }
        if (code in 0xD800..0xDBFF) {
            if (
                positon + 6 > data.size || data[positon] != GhostJsonConstants.BACKSLASH ||
                data[positon + 1] != 'u'.code.toByte()
            ) {
                throwError("Lone high surrogate: \\u$hex")
            }
            internalSkip(2)
            val lowHex = data.decodeToString(positon, positon + 4)
            internalSkip(4)
            val lowCode = try {
                lowHex.toInt(16)
            } catch (_: Exception) {
                throwError("Invalid low surrogate: \\u$lowHex")
            }
            if (lowCode !in 0xDC00..0xDFFF) {
                throwError("Invalid low surrogate: \\u$lowHex")
            }
            return (((code - 0xD800) shl 10) or (lowCode - 0xDC00)) + 0x10000
        }
        if (code in 0xDC00..0xDFFF) {
            throwError("Lone low surrogate: \\u$hex")
        }
        return code
    }

    internal fun skipQuotedString() {
        expectByte(GhostJsonConstants.QUOTE)
        skipQuotedStringBody()
    }

    internal fun skipQuotedStringBody() {
        while (positon < data.size) {
            val currentByte = data[positon]
            internalSkip(1)
            if (currentByte == GhostJsonConstants.QUOTE) return
            if (currentByte == GhostJsonConstants.BACKSLASH) {
                if (strictMode) {
                    readEscapeCode()
                } else if (positon < data.size) {
                    if (data[positon] == 'u'.code.toByte()) {
                        internalSkip(1);
                        val skipLength = minOf(4, data.size - positon)
                        internalSkip(skipLength)
                    } else {
                        internalSkip(1)
                    }
                }
            } else if (currentByte.toInt() in 0..31) {
                throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun skipWhitespace() {
        if (nextTokenByte != -1) return

        while (positon < data.size) {
            val currentByte = data[positon].toInt() and 0xFF

            // Bitmask check for 0x20 (space), 0x0A (LF), 0x0D (CR), 0x09 (TAB)
            // (1L << 32) | (1L << 10) | (1L << 13) | (1L << 9) = 0x100002600
            if (currentByte > 32 || (0x100002600L and (1L shl currentByte)) == 0L) return

            positon++
            // Word-skipping Overdrive
            while (positon + 3 < data.size) {
                val word1 = data[positon].toInt() and 0xFF
                val word2 = data[positon + 1].toInt() and 0xFF
                val word3 = data[positon + 2].toInt() and 0xFF
                val word4 = data[positon + 3].toInt() and 0xFF

                if (word1 <= 32 && (0x100002600L and (1L shl word1)) != 0L &&
                    word2 <= 32 && (0x100002600L and (1L shl word2)) != 0L &&
                    word3 <= 32 && (0x100002600L and (1L shl word3)) != 0L &&
                    word4 <= 32 && (0x100002600L and (1L shl word4)) != 0L
                ) {
                    positon += 4
                } else break
            }
        }
    }

    internal fun peekByte(): Byte {
        if (positon >= data.size) {
            throwError(GhostJsonConstants.UNEXPECTED_EOF_ERROR)
        }
        return data[positon]
    }

    internal fun peekNextByte(offset: Long): Byte? {
        val index = positon + offset.toInt()
        if (index >= data.size) return null
        return data[index]
    }

    fun throwError(msg: String): Nothing {
        var line = 1
        var column = 1
        for (i in 0 until positon) {
            if (data[i] == GhostJsonConstants.NEWLINE) {
                line++; column = 1
            } else {
                column++
            }
        }
        throw GhostJsonException(msg, line, column)
    }

    internal fun checkDepth() {
        if (depth >= maxDepth) {
            throwError("${GhostJsonConstants.ERR_DEPTH_EXCEEDED} ($maxDepth)")
        }
    }
}