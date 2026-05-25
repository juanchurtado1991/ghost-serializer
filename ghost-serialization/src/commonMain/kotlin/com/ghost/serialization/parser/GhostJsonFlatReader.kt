@file:OptIn(InternalGhostApi::class)
@file:Suppress("FunctionName")

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity

/**
 * Ultra-fast, specialized JSON parser for Kotlin Multiplatform that operates directly
 * on a flat [ByteArray] without any interface dispatch or hasFastPath boundaries.
 */
class GhostJsonFlatReader(
    @PublishedApi internal var rawData: ByteArray,
    var maxDepth: Int = C.MAX_DEPTH,
    var strictMode: Boolean = false,
    var coerceStringsToNumbers: Boolean = false,
    var coerceBooleans: Boolean = false,
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) {

    @PublishedApi
    internal val source = ByteArrayGhostSource(rawData)

    @PublishedApi
    internal var limit: Int = rawData.size

    @PublishedApi
    internal var position: Int = 0

    @PublishedApi
    internal var nextTokenByte: Int = C.RESET_TOKEN_BYTE

    @InternalGhostApi
    fun _getPosition(): Int = position

    @InternalGhostApi
    fun _setPosition(p: Int) {
        position = p
    }

    @InternalGhostApi
    fun _getRawData(): ByteArray = rawData

    @InternalGhostApi
    fun _setNextTokenByte(t: Int) {
        nextTokenByte = t
    }

    internal val stringPool = arrayOfNulls<String>(C.STR_POOL_SIZE)

    internal var lastScanContentWas7BitOnly: Boolean = false

    var depth: Int = 0

    /**
     * Gets the byte at the specified index, masking it to a positive integer.
     */
    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun getByte(index: Int): Int {
        return rawData[index].toInt() and C.BYTE_MASK
    }

    /**
     * Helper to verify if the given byte is a valid JSON numeric digit.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (byteCode xor C.ZERO_INT) < C.BASE_TEN
    }

    /**
     * Throws a structured [GhostJsonException] with exact position, line, and column numbers.
     */
    fun throwError(message: String): Nothing {
        val errorPosition = position
        val errorEnd = if (errorPosition > limit) {
            limit
        } else {
            errorPosition
        }

        throw GhostJsonException(
            baseMessage = "$message at position $errorPosition",
            computeLineCol = {
                var columnNumber = 0
                var lineNumber = 0
                var byteIndex = 0
                while (byteIndex < errorEnd) {
                    if ((rawData[byteIndex].toInt() and C.BYTE_MASK) == C.NEWLINE_INT) {
                        lineNumber++
                        columnNumber = 0
                    } else {
                        columnNumber++
                    }
                    byteIndex++
                }
                intArrayOf(lineNumber, columnNumber)
            }
        )
    }

    /**
     * Skips forward in the byte array by [n] bytes and resets [nextTokenByte].
     */
    fun internalSkip(n: Int) {
        position += n
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    /**
     * Advances the position past any whitespace and caches the next non-whitespace token byte.
     */
    fun skipWhitespace() {
        val localData = rawData
        val nextPos = findNextNonWhitespaceImpl(position, limit) {
            localData[it].toInt() and C.BYTE_MASK
        }

        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = localData[position].toInt() and C.BYTE_MASK
        } else {
            position = limit
            nextTokenByte = C.MATCH_END
        }
    }

    /**
     * Peeks at the next key to see if it matches the discriminator name without consuming it.
     */
    fun peekDiscriminator(key: String = C.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == C.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(C.TYPE_BS)
        }
        return peekDiscriminator(key.encodeUtf8())
    }

    /**
     * Peeks at the next key to see if it matches the discriminator byte string without consuming it.
     */
    fun peekDiscriminator(key: ByteString): String? {
        return GhostDiscriminatorPeeker.peek(
            source,
            rawData,
            false,
            position,
            limit,
            key
        )
    }

    /**
     * Peeks and returns the next token byte in the stream, skipping preceding whitespaces.
     */
    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) {
            return cached
        }
        skipWhitespace()
        return nextTokenByte
    }

    /**
     * Peeks and returns the next token byte as a [Byte].
     */
    fun peekByte(): Byte = peekNextToken().toByte()

    /**
     * Consumes and returns the next non-whitespace token byte in the stream.
     */
    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) {
            throwError(C.ERR_UNEXPECTED_EOF)
        }
        internalSkip(1)
        return nextToken
    }

    /**
     * Skips and validates that the next characters in the stream match the [expected] byte sequence.
     */
    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        val size = expected.size
        if (position + size > limit || !expected.rangeEquals(0, rawData, position, size)) {
            throwError("Expected literal ${expected.utf8()}")
        }
        position += size
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    /**
     * Resets the reader's state to process a new byte payload.
     */
    fun reset(newData: ByteArray, newLimit: Int = newData.size) {
        this.rawData = newData
        source.data = newData
        this.position = 0
        this.limit = newLimit
        this.nextTokenByte = C.RESET_TOKEN_BYTE
        this.depth = 0
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.coerceBooleans = false
        this.maxDepth = C.MAX_DEPTH
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
        this.lastScanContentWas7BitOnly = false
    }

    /**
     * Begins consumption of a JSON object '{'. Increments validation depth.
     */
    fun beginObject() {
        if (nextNonWhitespace() != C.OPEN_OBJ_INT) {
            throwError(C.ERR_EXPECTED_BEGIN_OBJ)
        }
        depth++
        if (depth > maxDepth) {
            throwError(C.ERR_DEPTH_EXCEEDED)
        }
    }

    /**
     * Ends consumption of a JSON object '}'. Decrements validation depth.
     */
    fun endObject() {
        if (nextNonWhitespace() != C.CLOSE_OBJ_INT) {
            throwError(C.ERR_EXPECTED_END_OBJ)
        }
        depth--
    }

    /**
     * Begins consumption of a JSON array '['. Increments validation depth.
     */
    fun beginArray() {
        if (nextNonWhitespace() != C.OPEN_ARR_INT) {
            throwError(C.ERR_EXPECTED_BEGIN_ARR)
        }
        depth++
        if (depth > maxDepth) {
            throwError(C.ERR_DEPTH_EXCEEDED)
        }
    }

    /**
     * Ends consumption of a JSON array ']'. Decrements validation depth.
     */
    fun endArray() {
        if (nextNonWhitespace() != C.CLOSE_ARR_INT) {
            throwError(C.ERR_EXPECTED_END_ARR)
        }
        depth--
    }

    /**
     * Checks if there are more elements in the current JSON container.
     */
    fun hasNext(): Boolean {
        val token = peekNextToken()
        if (
            token == C.CLOSE_ARR_INT ||
            token == C.CLOSE_OBJ_INT ||
            token == C.MATCH_END
        ) {
            return false
        }
        if (token == C.COMMA_INT) {
            internalSkip(1)
            val next = peekNextToken()
            if (next == C.CLOSE_ARR_INT || next == C.CLOSE_OBJ_INT) {
                throwError(C.ERR_TRAILING_COMMA)
            }
        }
        return true
    }

    /**
     * Consumes any comma separator and returns the next object key string. Returns null if object ends.
     */
    fun nextKey(): String? {
        val token = peekNextToken()
        if (token == C.CLOSE_OBJ_INT) {
            return null
        }
        if (token == C.COMMA_INT) {
            internalSkip(1)
            if (peekNextToken() == C.CLOSE_OBJ_INT) {
                throwError(C.ERR_TRAILING_COMMA)
            }
        }
        return readQuotedString()
    }

    /**
     * Consumes the ':' key-value separator character.
     */
    fun consumeKeySeparator() {
        if (nextNonWhitespace() != C.COLON_INT) {
            throwError(C.ERR_EXPECTED_COLON)
        }
    }

    /**
     * Consumes the array element separating comma if present.
     */
    fun consumeArraySeparator() {
        if (peekNextToken() == C.COMMA_INT) {
            internalSkip(1)
        }
    }

    /**
     * Parses and returns the next [Boolean] value.
     */
    fun nextBoolean(): Boolean {
        val token = peekNextToken()
        if (token == C.TRUE_CHAR_INT) {
            skipAndValidateLiteral(C.TRUE_BS)
            return true
        }
        if (token == C.FALSE_CHAR_INT) {
            skipAndValidateLiteral(C.FALSE_BS)
            return false
        }
        if (coerceBooleans) {
            if (token == C.ONE_INT) {
                internalSkip(1)
                return true
            }
            if (token == C.ZERO_INT) {
                internalSkip(1)
                return false
            }
            if (token == C.QUOTE_INT) {
                val s = readQuotedString().lowercase()
                return when (s) {
                    C.COERCE_TRUE_STR,
                    C.COERCE_YES_STR,
                    C.COERCE_ON_STR,
                    C.COERCE_1_STR,
                    C.COERCE_Y_STR -> true

                    C.COERCE_FALSE_STR,
                    C.COERCE_NO_STR,
                    C.COERCE_OFF_STR,
                    C.COERCE_0_STR,
                    C.COERCE_N_STR -> false

                    else -> throwError("${C.ERR_EXPECTED_BOOLEAN} \"$s\"")
                }
            }
        }
        throwError(C.ERR_EXPECTED_BOOLEAN)
    }

    /**
     * Parses and returns the next string literal.
     */
    fun nextString(): String = readQuotedString()

    /**
     * Peeks whether the next JSON token is the null value token.
     */
    fun isNextNullValue(): Boolean = peekNextToken() == C.NULL_CHAR_INT

    /**
     * Consumes the null value literal from the stream.
     */
    fun consumeNull() {
        skipAndValidateLiteral(C.NULL_BS)
    }

    /**
     * Selects name and consumes the key separator.
     */
    fun selectNameAndConsume(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = true)

    /**
     * Selects matching string options.
     */
    fun selectString(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = false)

    /**
     * Low-level helper to search and return matched index in options lookup table.
     */
    private fun internalSelect(options: JsonReaderOptions, consumeSeparator: Boolean): Int {
        var token = peekNextToken()
        if (token == C.CLOSE_OBJ_INT) {
            return -1
        }

        if (token == C.COMMA_INT) {
            internalSkip(1)
            token = peekNextToken()
            if (token == C.CLOSE_OBJ_INT) {
                throwError(C.ERR_TRAILING_COMMA)
            }
        }

        if (token != C.QUOTE_INT) {
            throwError(
                if (consumeSeparator) {
                    C.ERR_EXPECTED_KEY
                } else {
                    C.ERR_EXPECTED_STRING
                }
            )
        }

        val start = position + 1
        val localData = rawData
        val end = findClosingQuoteImpl(start, limit) {
            localData[it].toInt() and C.BYTE_MASK
        }

        if (end == -1) {
            throwError(C.UNTERMINATED_STRING_ERROR)
        }

        val length = end - start
        val key = computeKeyHash(start, length)

        val hasIndex = ((key * options.multiplier + length) shr options.shift) and C.HASH_MASK
        val index = options.dispatch[hasIndex]

        if (index != C.MATCH_END) {
            if (verifyKeyMatch(start, length, options.rawBytes[index], consumeSeparator)) {
                return index
            }
        }

        // No match found
        val newPos = end + 1
        position = newPos
        nextTokenByte = C.MATCH_END
        if (consumeSeparator) {
            if (newPos < limit && getByte(newPos) == C.COLON_INT) {
                position = newPos + 1
            } else {
                consumeKeySeparator()
            }
        } else if (strictMode) {
            val unknownKey = source.decodeToString(start, end)
            throwError("${C.STRICT_MODE_UNKNOWN_FIELD}$unknownKey")
        }

        return C.MATCH_NONE
    }

    /**
     * Computes the 32-bit hash value of the string range.
     */
    private fun computeKeyHash(start: Int, length: Int): Int {
        var key = 0
        if (length >= 4) {
            val b0 = rawData[start].toInt() and C.BYTE_MASK
            val b1 = rawData[start + 1].toInt() and C.BYTE_MASK
            val b2 = rawData[start + 2].toInt() and C.BYTE_MASK
            val b3 = rawData[start + 3].toInt() and C.BYTE_MASK
            key = b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
        } else {
            if (length >= 1) {
                key = key or (rawData[start].toInt() and C.BYTE_MASK)
            }
            if (length >= 2) {
                key = key or ((rawData[start + 1].toInt() and C.BYTE_MASK) shl C.SHIFT_8)
            }
            if (length >= 3) {
                key = key or ((rawData[start + 2].toInt() and C.BYTE_MASK) shl C.SHIFT_16)
            }
        }
        return key
    }

    /**
     * Performs a fast comparison of the parsed string against expected bytes to verify matches.
     */
    private fun verifyKeyMatch(
        start: Int,
        length: Int,
        expected: ByteArray,
        consumeSeparator: Boolean
    ): Boolean {
        // Length is already guaranteed equal by the dispatch table (same hash slot).
        if (expected.size == length) {
            val localData = rawData
            var i = 0
            // Unrolled x4 for typical ASCII field name lengths (4–20 chars).
            while (i + 3 < length) {
                if (localData[start + i] != expected[i]) return false
                if (localData[start + i + 1] != expected[i + 1]) return false
                if (localData[start + i + 2] != expected[i + 2]) return false
                if (localData[start + i + 3] != expected[i + 3]) return false
                i += 4
            }
            while (i < length) {
                if (localData[start + i] != expected[i]) return false
                i++
            }
            val endPos = start + length
            val newPos = endPos + 1
            position = newPos
            nextTokenByte = -1
            if (consumeSeparator) {
                if (newPos < limit) {
                    val colonToken = getByte(newPos)
                    if (colonToken == C.COLON_INT) {
                        position = newPos + 1
                    } else {
                        consumeKeySeparator()
                    }
                } else {
                    consumeKeySeparator()
                }
            }
            return true
        }
        return false
    }


    /**
     * Peeks at a key name and returns it if it is a string match.
     */
    fun peekStringField(name: String): String? {
        return peekDiscriminator(name)
    }

    /**
     * Skips the next complete value token (object, array, string, number, boolean, null) from the stream.
     */
    fun skipValue() {
        val token = peekNextToken()
        when (token) {
            C.OPEN_OBJ_INT -> {
                beginObject()
                while (hasNext()) {
                    if (peekNextToken() != C.QUOTE_INT) {
                        throwError(C.ERR_EXPECTED_KEY)
                    }
                    skipQuotedString()
                    consumeKeySeparator()
                    skipValue()
                }
                endObject()
            }

            C.OPEN_ARR_INT -> {
                beginArray()
                while (hasNext()) {
                    skipValue()
                }
                endArray()
            }

            C.QUOTE_INT -> {
                skipQuotedString()
            }
            C.TRUE_CHAR_INT -> {
                skipAndValidateLiteral(C.TRUE_BS)
            }
            C.FALSE_CHAR_INT -> {
                skipAndValidateLiteral(C.FALSE_BS)
            }
            C.NULL_CHAR_INT -> {
                skipAndValidateLiteral(C.NULL_BS)
            }

            else -> {
                skipNumber()
            }
        }
    }

    /**
     * Reads a list of items using the provided [itemParser].
     */
    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        beginArray()
        if (peekNextToken() == C.CLOSE_ARR_INT) {
            endArray()
            return emptyList()
        }
        val list = ArrayList<T>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            list.add(itemParser())
            val next = nextNonWhitespace()
            if (next == C.CLOSE_ARR_INT) {
                depth--
                break
            }
            if (next != C.COMMA_INT) {
                throwError("${C.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found $next")
            }
            if (list.size > maxSize) {
                throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
            }
        }
        return list
    }

    /**
     * Reads a map of keys and values using the provided [keyParser] and [valueParser].
     */
    inline fun <K, V> readMap(
        crossinline keyParser: () -> K,
        crossinline valueParser: () -> V
    ): Map<K, V> {
        beginObject()
        if (peekNextToken() == C.CLOSE_OBJ_INT) {
            endObject()
            return emptyMap()
        }

        val map = HashMap<K, V>(initialCollectionCapacity)
        val maxSize = maxCollectionSize

        while (true) {
            val key = keyParser()
            consumeKeySeparator()
            val value = valueParser()
            map[key] = value

            val next = nextNonWhitespace()
            if (next == C.CLOSE_OBJ_INT) {
                depth--
                break
            }
            if (next != C.COMMA_INT) {
                throwError("${C.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ} but found $next")
            }
            if (map.size > maxSize) {
                throwError("${C.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
            }
        }
        return map
    }

    /**
     * Resiliently decodes a value. If an error occurs, skips the value and returns null.
     */
    @InternalGhostApi
    inline fun <T> decodeResilient(crossinline block: () -> T): T? {
        val savedPos = position
        val savedToken = nextTokenByte
        try {
            return block()
        } catch (_: GhostJsonException) {
            position = savedPos
            nextTokenByte = savedToken
            skipValue()
            return null
        }
    }

    companion object {
        @InternalGhostApi
        const val RESET_TOKEN_BYTE: Int = -1
    }
}
