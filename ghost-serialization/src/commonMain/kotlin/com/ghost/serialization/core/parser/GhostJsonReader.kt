package com.ghost.serialization.core.parser

import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.InternalGhostApi
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
@OptIn(InternalGhostApi::class)
class GhostJsonReader(
    @InternalGhostApi var source: GhostSource,
    @InternalGhostApi var limit: Int = source.size,
    @PublishedApi
    internal val maxDepth: Int = 255,
    internal var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    @PublishedApi
    internal val maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {
    @PublishedApi
    internal fun checkCollectionSize(currentSize: Int) {
        if (currentSize >= maxCollectionSize) {
            throwError("Reached maximum collection size limit ($maxCollectionSize)")
        }
    }

    fun reset(newSource: GhostSource, newLimit: Int = newSource.size) {
        this.source = newSource
        this.limit = newLimit
        this.position = 0
        this.depth = 0
        this.nextTokenByte = -1
        this.strictMode = false
        this.coerceStringsToNumbers = false
    }

    fun reset(newData: ByteArray) = reset(ByteArraySource(newData))

    /**
     * Releases the data buffer to prevent memory leaks in recycled scenarios.
     */
    fun clear() {
        this.source = ByteArraySource(byteArrayOf())
        this.limit = 0
        this.position = 0
        this.nextTokenByte = -1
        this.stringPool.fill(null)
    }

    constructor(
        source: BufferedSource,
        maxDepth: Int = 255,
        strictMode: Boolean = false
    ) : this(
        source = ByteArraySource(source.readByteArray()),
        maxDepth = maxDepth,
        strictMode = strictMode
    )
    
    constructor(
        data: ByteArray,
        maxDepth: Int = 255,
        strictMode: Boolean = false
    ) : this(
        source = ByteArraySource(data),
        maxDepth = maxDepth,
        strictMode = strictMode
    )

    @PublishedApi
    internal var position: Int = 0
        set(value) {
            field = value
            nextTokenByte = -1
        }

    @PublishedApi
    internal var depth: Int = 0

    @PublishedApi
    internal var nextTokenByte: Int = -1
        set(value) {
            field = value
        }

    val path: String get() = GhostJsonConstants.PATH_ROOT
    internal val stringPool = arrayOfNulls<String>(
        GhostJsonConstants.STR_POOL_SIZE
    )

    @PublishedApi
    internal fun internalSkip(number: Int) {
        position += number
    }

    internal fun expectByte(expected: Byte) {
        if (position >= limit) {
            throwError("Expected '${expected.toInt().toChar()}' but reached end")
        }

        val actual = source[position++]
        if (actual != expected) {
            throwError(
                "Expected '${expected.toInt().toChar()}' but found '${
                    actual.toInt().toChar()
                }'"
            )
        }
    }

    internal fun internalSelect(options: JsonReaderOptions): Int {
        val remaining = limit - position
        if (remaining <= 0) return -2

        var length = 0
        // Vectorized scan for the closing quote
        while (position + length + 7 < limit) {
            if (source[position + length] == GhostJsonConstants.QUOTE) break
            if (source[position + length + 1] == GhostJsonConstants.QUOTE) {
                length += 1; break
            }
            if (source[position + length + 2] == GhostJsonConstants.QUOTE) {
                length += 2; break
            }
            if (source[position + length + 3] == GhostJsonConstants.QUOTE) {
                length += 3; break
            }
            if (source[position + length + 4] == GhostJsonConstants.QUOTE) {
                length += 4; break
            }
            if (source[position + length + 5] == GhostJsonConstants.QUOTE) {
                length += 5; break
            }
            if (source[position + length + 6] == GhostJsonConstants.QUOTE) {
                length += 6; break
            }
            if (source[position + length + 7] == GhostJsonConstants.QUOTE) {
                length += 7; break
            }
            length += 8
        }

        while (position + length < limit && source[position + length] != GhostJsonConstants.QUOTE) {
            length++
        }

        if (position + length >= limit) return -2

        // Multi-byte hash calculation
        var key = 0
        if (length >= 1) key = key or (source[position].toInt() and 0xFF)
        if (length >= 2) key = key or ((source[position + 1].toInt() and 0xFF) shl 8)
        if (length >= 3) key = key or ((source[position + 2].toInt() and 0xFF) shl 16)
        if (length >= 4) key = key or ((source[position + 3].toInt() and 0xFF) shl 24)

        val hashIndex = ((key * options.multiplier) + length) shr options.shift and 1023
        val hint = options.dispatch[hashIndex]

        if (hint != -1) {
            val optionBytes = options.rawBytes[hint]
            if (optionBytes.size == length) {
                var i = 0
                var match = true
                while (i + 3 < length) {
                    if (
                        source[position + i] != optionBytes[i] || source[position + i + 1] != optionBytes[i + 1] ||
                        source[position + i + 2] != optionBytes[i + 2] || source[position + i + 3] != optionBytes[i + 3]
                    ) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < length) {
                        if (source[position + i] != optionBytes[i]) {
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
                        source[position + i] != optionBytes[i] || source[position + i + 1] != optionBytes[i + 1] ||
                        source[position + i + 2] != optionBytes[i + 2] || source[position + i + 3] != optionBytes[i + 3]
                    ) {
                        match = false; break
                    }
                    i += 4
                }
                if (match) {
                    while (i < length) {
                        if (source[position + i] != optionBytes[i]) {
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
        if (position >= limit) return -1

        var currentByte = if (nextTokenByte != -1) {
            nextTokenByte
        } else {
            (source[position].toInt() and 0xFF)
        }

        if (currentByte == GhostJsonConstants.COMMA.toInt()) { // COMMA
            position++; nextTokenByte = -1; skipWhitespace()
            if (position >= limit) return -1
            currentByte = source[position].toInt() and 0xFF
        }

        if (currentByte == GhostJsonConstants.CLOSE_OBJ.toInt()) return -1 // CLOSE_OBJ

        if (currentByte != GhostJsonConstants.QUOTE.toInt()) {
            throwError("Expected '\"' but found ${currentByte.toChar()}")
        }

        position++; nextTokenByte = -1 // Skip opening quote

        val index = internalSelect(options)
        if (index >= 0) {
            if (position < limit && source[position] == GhostJsonConstants.QUOTE) {
                position++; nextTokenByte = -1
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
        if (position >= limit) return -1

        var currentByte = if (nextTokenByte != -1) {
            nextTokenByte
        } else {
            (source[position].toInt() and 0xFF)
        }

        if (currentByte == GhostJsonConstants.COMMA.toInt()) { // COMMA
            position++; nextTokenByte = -1; skipWhitespace()
            if (position >= limit) return -1
            currentByte = source[position].toInt() and 0xFF
        }

        if (currentByte == GhostJsonConstants.CLOSE_OBJ.toInt()) return -1 // CLOSE_OBJ

        if (currentByte != GhostJsonConstants.QUOTE.toInt()) throwError("Expected '\"' but found ${currentByte.toChar()}")
        position++; nextTokenByte = -1 // Skip opening quote

        val index = internalSelect(options)
        if (index >= 0) {
            // Found it. Skip closing quote and separator
            if (position < limit && source[position] == GhostJsonConstants.QUOTE) {
                position++
                // Fused consumeKeySeparator
                skipWhitespace()
                if (position < limit && source[position] == GhostJsonConstants.COLON) {
                    position++; nextTokenByte = -1
                    skipWhitespace()
                    if (position < limit) nextTokenByte = source[position].toInt() and 0xFF
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
        if (position + 4 > limit) {
            throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
        }
        val currentByte = source[position]
        if (currentByte == GhostJsonConstants.TRUE_CHAR) {
            if (source[position + 1] == 'r'.code.toByte() &&
                source[position + 2] == 'u'.code.toByte() &&
                source[position + 3] == 'e'.code.toByte()
            ) {
                internalSkip(4); return true
            }
        } else if (currentByte == GhostJsonConstants.FALSE_CHAR) {
            if (position + 5 > limit) {
                throwError(GhostJsonConstants.TRUNCATED_LITERAL_ERROR)
            }
            if (source[position + 1] == 'a'.code.toByte() &&
                source[position + 2] == 'l'.code.toByte() &&
                source[position + 3] == 's'.code.toByte() &&
                source[position + 4] == 'e'.code.toByte()
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
        val start = position
        // Ghost Vectorized Scan: Find quote or escape faster using Lookup Table
        while (position + 3 < limit) {
            val byte1 = source[position].toInt() and 0xFF
            val byte2 = source[position + 1].toInt() and 0xFF
            val byte3 = source[position + 2].toInt() and 0xFF
            val byte4 = source[position + 3].toInt() and 0xFF

            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte1]) break
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte2]) {
                position += 1; break
            }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte3]) {
                position += 2; break
            }
            if (GhostJsonConstants.IS_STRING_TERMINATOR[byte4]) {
                position += 3; break
            }
            position += 4
        }

        while (position < limit) {
            val currentByte = source[position]
            if (currentByte == GhostJsonConstants.QUOTE) {
                val length = position - start
                return readPooledString(start, length)
            }
            if (currentByte.toInt() in 0..31) throwError(GhostJsonConstants.UNESCAPED_CONTROL_CHAR_ERROR)
            if (currentByte == GhostJsonConstants.BACKSLASH) return readStringWithEscapes(start)
            position++
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readPooledString(start: Int, length: Int): String {
        if (length <= 0) {
            internalSkip(1); return ""
        }
        if (length > GhostJsonConstants.MAX_POOL_STRING_LENGTH) {
            val result = source.decodeToString(start, start + length)
            internalSkip(1); return result
        }
        // Optimized Rolling Hash with loop unrolling
        var rollingHash = 0
        var i = start
        val end = start + length

        while (i + 3 < end) {
            rollingHash = (rollingHash shl 5) - rollingHash + (source[i].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (source[i + 1].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (source[i + 2].toInt() and 0xFF)
            rollingHash = (rollingHash shl 5) - rollingHash + (source[i + 3].toInt() and 0xFF)
            i += 4
        }
        while (i < end) {
            rollingHash = (rollingHash shl 5) - rollingHash + (source[i].toInt() and 0xFF)
            i++
        }

        val poolIndex = rollingHash and (GhostJsonConstants.STR_POOL_SIZE - 1)
        val cached = stringPool[poolIndex]

        if (cached != null && cached.length == length) {
            // Hot Path: Candidate found, verify bytes with unrolling
            var match = true
            if (length >= 4) {
                if (cached[0].code != (source[start].toInt() and 0xFF) ||
                    cached[1].code != (source[start + 1].toInt() and 0xFF) ||
                    cached[2].code != (source[start + 2].toInt() and 0xFF) ||
                    cached[3].code != (source[start + 3].toInt() and 0xFF)
                ) {
                    match = false
                }
            }
            if (match) {
                var j = if (length >= 4) 4 else 0
                while (j < length) {
                    if (cached[j].code != (source[start + j].toInt() and 0xFF)) {
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
        val result = source.decodeToString(start, start + length)
        stringPool[poolIndex] = result
        internalSkip(1) // Skip closing quote
        return result
    }

    private fun readStringWithEscapes(start: Int): String {
        val output = StringBuilder(GhostJsonConstants.STRING_BUILDER_CAPACITY)
        if (position > start) {
            output.append(source.decodeToString(start, position))
        }
        while (position < limit) {
            val currentByte = source[position]
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
                val scanStart = position
                while (position < limit) {
                    val subByte = source[position]
                    if (
                        subByte == GhostJsonConstants.QUOTE ||
                        subByte == GhostJsonConstants.BACKSLASH ||
                        subByte.toInt() in 0..31
                    ) {
                        break
                    }
                    internalSkip(1)
                }
                output.append(source.decodeToString(scanStart, position))
            }
        }
        throwError(GhostJsonConstants.UNTERMINATED_STRING_ERROR)
    }

    private fun readEscapeCode(): Int {
        if (position >= limit) {
            throwError(GhostJsonConstants.UNTERMINATED_ESCAPE_ERROR)
        }

        val escapeByte = source[position]
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
        if (position + 4 > limit) {
            throwError(GhostJsonConstants.UNTERMINATED_UNICODE_ERROR)
        }

        val hex = source.decodeToString(position, position + 4)
        internalSkip(4)
        val code = try {
            hex.toInt(16)
        } catch (_: Exception) {
            throwError("Invalid unicode escape: \\u$hex")
        }
        if (code in 0xD800..0xDBFF) {
            if (position + 6 > limit || source[position] != GhostJsonConstants.BACKSLASH ||
                source[position + 1] != 'u'.code.toByte()
            ) {
                throwError("Lone high surrogate: \\u$hex")
            }
            internalSkip(2)
            val lowHex = source.decodeToString(position, position + 4)
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
        while (position < limit) {
            val currentByte = source[position]
            internalSkip(1)
            if (currentByte == GhostJsonConstants.QUOTE) return
            if (currentByte == GhostJsonConstants.BACKSLASH) {
                if (strictMode) {
                    readEscapeCode()
                } else if (position < limit) {
                    if (source[position] == 'u'.code.toByte()) {
                        internalSkip(1);
                        val skipLength = minOf(4, limit - position)
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

        while (position < limit) {
            val currentByte = source[position].toInt() and 0xFF

            // Bitmask check for 0x20 (space), 0x0A (LF), 0x0D (CR), 0x09 (TAB)
            // (1L << 32) | (1L << 10) | (1L << 13) | (1L << 9) = 0x100002600
            if (currentByte > 32 || (0x100002600L and (1L shl currentByte)) == 0L) return

            position++
            // Word-skipping Optimization
            while (position + 3 < limit) {
                val word1 = source[position].toInt() and 0xFF
                val word2 = source[position + 1].toInt() and 0xFF
                val word3 = source[position + 2].toInt() and 0xFF
                val word4 = source[position + 3].toInt() and 0xFF

                if (word1 <= 32 && (0x100002600L and (1L shl word1)) != 0L &&
                    word2 <= 32 && (0x100002600L and (1L shl word2)) != 0L &&
                    word3 <= 32 && (0x100002600L and (1L shl word3)) != 0L &&
                    word4 <= 32 && (0x100002600L and (1L shl word4)) != 0L
                ) {
                    position += 4
                } else break
            }
        }
    }

    internal fun peekByte(): Byte {
        if (position >= limit) {
            throwError(GhostJsonConstants.UNEXPECTED_EOF_ERROR)
        }
        return source[position]
    }

    internal fun peekNextByte(offset: Long): Byte? {
        val index = position + offset.toInt()
        if (index >= limit) return null
        return source[index]
    }

    fun throwError(msg: String): Nothing {
        var line = 1
        var column = 1
        for (i in 0 until position) {
            if (source[i] == GhostJsonConstants.NEWLINE) {
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