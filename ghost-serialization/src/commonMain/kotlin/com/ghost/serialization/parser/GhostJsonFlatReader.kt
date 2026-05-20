@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.releaseScratchBuffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import kotlin.math.pow

/**
 * Ultra-fast, specialized JSON parser for Kotlin Multiplatform that operates directly
 * on a flat [ByteArray] without any interface dispatch or hasFastPath boundaries.
 */
class GhostJsonFlatReader(
    @InternalGhostApi var rawData: ByteArray,
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

    @InternalGhostApi
    var position: Int = 0

    @InternalGhostApi
    var nextTokenByte: Int = C.RESET_TOKEN_BYTE

    internal val stringPool = arrayOfNulls<String>(C.STR_POOL_SIZE)

    internal var lastScanContentWas7BitOnly: Boolean = false

    var depth: Int = 0

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun getByte(index: Int): Int {
        return rawData[index].toInt() and C.BYTE_MASK
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun isDigit(byteCode: Int): Boolean {
        return (C.DIGIT_BITMASK shr byteCode) and C.BYTE_SHIFT_UNIT != C.RESULT_NONE
    }

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

    fun internalSkip(n: Int) {
        position += n
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    fun skipWhitespace() {
        val nextPos = source.findNextNonWhitespace(position, limit)
        if (nextPos != -1) {
            position = nextPos
            nextTokenByte = getByte(position)
        } else {
            position = limit
            nextTokenByte = C.MATCH_END
        }
    }

    fun peekDiscriminator(key: String = C.DEFAULT_DISCRIMINATOR_KEY): String? {
        if (key == C.DEFAULT_DISCRIMINATOR_KEY) {
            return peekDiscriminator(C.TYPE_BS)
        }
        return peekDiscriminator(key.encodeUtf8())
    }

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

    fun peekNextToken(): Int {
        val cached = nextTokenByte
        if (cached != -1) { return cached }
        skipWhitespace()
        return nextTokenByte
    }

    fun peekByte(): Byte = peekNextToken().toByte()

    fun nextNonWhitespace(): Int {
        val nextToken = peekNextToken()
        if (nextToken == -1) {
            throwError(C.ERR_UNEXPECTED_EOF)
        }
        internalSkip(1)
        return nextToken
    }

    @InternalGhostApi
    fun skipAndValidateLiteral(expected: ByteString) {
        if (!source.contentEquals(position, expected)) {
            throwError("Expected literal ${expected.utf8()}")
        }
        position += expected.size
        nextTokenByte = C.RESET_TOKEN_BYTE
    }

    fun readQuotedString(): String {
        return readQuotedStringImpl(
            getPosition = { position },
            setPosition = { position = it },
            getLimit = { limit },
            getNextNonWhitespace = { nextNonWhitespace() },
            setNextTokenByte = { nextTokenByte = it },
            getByte = { getByte(it) },
            getStringPool = { stringPool },
            setLastScanContentWas7BitOnly = { lastScanContentWas7BitOnly = it },
            scanString = { p, lim -> source.scanString(p, lim) },
            decodeJsonStringRange = { s, e, asc -> source.decodeJsonStringRange(s, e, asc) },
            contentEqualsString = { s, len, str -> source.contentEqualsString(s, len, str) },
            parseUnicodeHex = { parseUnicodeHex(it) },
            throwError = { throwError(it) }
        )
    }

    fun skipQuotedString() {
        skipQuotedStringImpl(
            getPosition = { position },
            setPosition = { position = it },
            getLimit = { limit },
            getNextNonWhitespace = { nextNonWhitespace() },
            setNextTokenByte = { nextTokenByte = it },
            getByte = { getByte(it) },
            findClosingQuote = { p, lim -> source.findClosingQuote(p, lim) },
            parseUnicodeHex = { parseUnicodeHex(it) },
            throwError = { throwError(it) }
        )
    }

    private fun parseUnicodeHex(currentPosition: Int): Int {
        return parseUnicodeHexImpl(
            currentPosition = currentPosition,
            getByte = { getByte(it) },
            throwError = { throwError(it) }
        )
    }

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

    fun beginObject() {
        beginObjectImpl(
            nextNonWhitespace = { nextNonWhitespace() },
            getDepth = { depth },
            setDepth = { depth = it },
            maxDepth = maxDepth,
            throwError = { throwError(it) }
        )
    }

    fun endObject() {
        endObjectImpl(
            nextNonWhitespace = { nextNonWhitespace() },
            getDepth = { depth },
            setDepth = { depth = it },
            throwError = { throwError(it) }
        )
    }

    fun beginArray() {
        beginArrayImpl(
            nextNonWhitespace = { nextNonWhitespace() },
            getDepth = { depth },
            setDepth = { depth = it },
            maxDepth = maxDepth,
            throwError = { throwError(it) }
        )
    }

    fun endArray() {
        endArrayImpl(
            nextNonWhitespace = { nextNonWhitespace() },
            getDepth = { depth },
            setDepth = { depth = it },
            throwError = { throwError(it) }
        )
    }

    fun hasNext(): Boolean {
        return hasNextImpl(
            peekNextToken = { peekNextToken() },
            internalSkip = { internalSkip(it) },
            throwError = { throwError(it) }
        )
    }

    fun nextKey(): String? {
        return nextKeyImpl(
            peekNextToken = { peekNextToken() },
            internalSkip = { internalSkip(it) },
            readQuotedString = { readQuotedString() },
            throwError = { throwError(it) }
        )
    }

    fun consumeKeySeparator() {
        consumeKeySeparatorImpl(
            nextNonWhitespace = { nextNonWhitespace() },
            throwError = { throwError(it) }
        )
    }

    fun consumeArraySeparator() {
        consumeArraySeparatorImpl(
            peekNextToken = { peekNextToken() },
            internalSkip = { internalSkip(it) }
        )
    }

    fun nextBoolean(): Boolean {
        return nextBooleanImpl(
            peekNextToken = { peekNextToken() },
            skipAndValidateLiteral = { skipAndValidateLiteral(it) },
            coerceBooleans = coerceBooleans,
            internalSkip = { internalSkip(it) },
            readQuotedString = { readQuotedString() },
            throwError = { throwError(it) }
        )
    }

    fun nextString(): String = readQuotedString()

    fun isNextNullValue(): Boolean = peekNextToken() == C.NULL_CHAR_INT

    fun consumeNull() {
        skipAndValidateLiteral(C.NULL_BS)
    }

    fun selectNameAndConsume(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = true)

    fun selectString(options: JsonReaderOptions): Int =
        internalSelect(options, consumeSeparator = false)

    private fun internalSelect(options: JsonReaderOptions, consumeSeparator: Boolean): Int {
        return internalSelectImpl(
            options = options,
            consumeSeparator = consumeSeparator,
            peekNextToken = { peekNextToken() },
            internalSkip = { internalSkip(it) },
            getPosition = { position },
            setPosition = { position = it },
            getLimit = { limit },
            findClosingQuote = { start, limit -> source.findClosingQuote(start, limit) },
            computeKeyHash = { start, length ->
                computeKeyHashImpl(start, length) {
                    rawData[it].toInt() and C.BYTE_MASK
                }
            },
            verifyKeyMatch = { start, length, expected, consumeSep ->
                verifyKeyMatchImpl(
                    start = start,
                    length = length,
                    expected = expected,
                    consumeSeparator = consumeSep,
                    setPosition = { position = it },
                    setNextTokenByte = { nextTokenByte = it },
                    getLimit = { limit },
                    getByte = {
                        rawData[it].toInt() and C.BYTE_MASK
                    },
                    consumeKeySeparator = { consumeKeySeparator() },
                    contentEquals = { startPos, expectedStr -> source.contentEquals(startPos, expectedStr) }
                )
            },
            setNextTokenByte = { nextTokenByte = it },
            getByte = {
                rawData[it].toInt() and C.BYTE_MASK
            },
            consumeKeySeparator = { consumeKeySeparator() },
            strictMode = strictMode,
            decodeToString = { start, end -> source.decodeToString(start, end) },
            throwError = { throwError(it) }
        )
    }

    fun peekStringField(name: String): String? {
        return peekDiscriminator(name)
    }

    fun skipValue() {
        skipValueImpl(
            peekNextToken = { peekNextToken() },
            beginObject = { beginObject() },
            hasNext = { hasNext() },
            skipQuotedString = { skipQuotedString() },
            consumeKeySeparator = { consumeKeySeparator() },
            skipValue = { skipValue() },
            endObject = { endObject() },
            beginArray = { beginArray() },
            endArray = { endArray() },
            skipAndValidateLiteral = { skipAndValidateLiteral(it) },
            skipNumber = { skipNumber() },
            throwError = { throwError(it) }
        )
    }

    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        return readListImpl(
            beginArray = { beginArray() },
            peekNextToken = { peekNextToken() },
            endArray = { endArray() },
            getInitialCollectionCapacity = { initialCollectionCapacity },
            getMaxCollectionSize = { maxCollectionSize },
            itemParser = { itemParser() },
            nextNonWhitespace = { nextNonWhitespace() },
            decrementDepth = { depth-- },
            throwError = { throwError(it) }
        )
    }

    inline fun <K, V> readMap(
        crossinline keyParser: () -> K,
        crossinline valueParser: () -> V
    ): Map<K, V> {
        return readMapImpl(
            beginObject = { beginObject() },
            peekNextToken = { peekNextToken() },
            endObject = { endObject() },
            getInitialCollectionCapacity = { initialCollectionCapacity },
            getMaxCollectionSize = { maxCollectionSize },
            keyParser = { keyParser() },
            consumeKeySeparator = { consumeKeySeparator() },
            valueParser = { valueParser() },
            nextNonWhitespace = { nextNonWhitespace() },
            decrementDepth = { depth-- },
            throwError = { throwError(it) }
        )
    }

    @InternalGhostApi
    inline fun <T> decodeResilient(crossinline block: () -> T): T? {
        return decodeResilientImpl(
            getPosition = { position },
            setPosition = { position = it },
            getNextTokenByte = { nextTokenByte },
            setNextTokenByte = { nextTokenByte = it },
            skipValue = { skipValue() },
            block = { block() }
        )
    }

    fun nextFloat(): Float = nextFloatImpl(
        prepareNumericHeader = { prepareNumericHeader() },
        validateLeadingZero = { validateLeadingZero() },
        readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
        throwError = { throwError(it) },
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        getByte = { getByte(it) },
        isExponentMarker = { isExponentMarker(it) },
        parseExponentValue = { parseExponentValue() },
        getFloatPowerOfTen = { getFloatPowerOfTen(it) },
        validateNumericRangeFloat = { validateNumericRangeFloat(it) },
        consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
        setNextTokenByte = { nextTokenByte = it }
    )

    fun nextDouble(): Double = nextDoubleImpl(
        prepareNumericHeader = { prepareNumericHeader() },
        validateLeadingZero = { validateLeadingZero() },
        readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
        throwError = { throwError(it) },
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        getByte = { getByte(it) },
        isExponentMarker = { isExponentMarker(it) },
        parseExponentValue = { parseExponentValue() },
        getDoublePowerOfTen = { getDoublePowerOfTen(it) },
        validateNumericRange = { validateNumericRange(it) },
        consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
        setNextTokenByte = { nextTokenByte = it }
    )

    @Suppress("NOTHING_TO_INLINE")
    private inline fun parseExponentValue(): Int {
        position++
        var isExpNegative = false
        if (position < limit) {
            val marker = getByte(position)
            if (marker == C.MINUS_INT) {
                isExpNegative = true
                position++
            } else if (marker == C.PLUS_INT) {
                position++
            }
        }

        var expValue = 0
        var hasExpDigits = false
        while (position < limit) {
            val currentByteInt = getByte(position)
            if (isDigit(currentByteInt)) {
                expValue = expValue * C.BASE_TEN + (currentByteInt - C.ZERO_INT)
                hasExpDigits = true
                position++
            } else break
        }

        if (!hasExpDigits) { throwError(C.ERR_EXPECTED_EXPONENT_DIGITS) }
        return if (isExpNegative) -expValue else expValue
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun getFloatPowerOfTen(exponent: Int): Float {
        return if (exponent > 0) {
            if (exponent < C.POWERS_OF_TEN_FLOAT.size) {
                C.POWERS_OF_TEN_FLOAT[exponent]
            } else {
                10.0f.pow(exponent.toFloat())
            }
        } else {
            val absExp = -exponent
            if (absExp < C.INVERSE_POWERS_OF_TEN_FLOAT.size) {
                C.INVERSE_POWERS_OF_TEN_FLOAT[absExp]
            } else {
                10.0f.pow(exponent.toFloat())
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun getDoublePowerOfTen(exponent: Int): Double {
        return if (exponent > 0) {
            if (exponent < C.POWERS_OF_TEN.size) {
                C.POWERS_OF_TEN[exponent]
            } else {
                10.0.pow(exponent.toDouble())
            }
        } else {
            val absExp = -exponent
            if (absExp < C.INVERSE_POWERS_OF_TEN.size) {
                C.INVERSE_POWERS_OF_TEN[absExp]
            } else {
                10.0.pow(exponent.toDouble())
            }
        }
    }

    fun nextInt(): Int = nextIntImpl(
        prepareNumericHeader = { prepareNumericHeader() },
        getPosition = { position },
        getLimit = { limit },
        getByte = { getByte(it) },
        handleLeadingZero = { handleLeadingZero() },
        parseIntDigits = { neg, start -> parseIntDigits(neg, start) },
        consumeNumericCoercionFooter = { consumeNumericCoercionFooter() }
    )

    fun nextLong(): Long = nextLongImpl(
        prepareNumericHeader = { prepareNumericHeader() },
        getPosition = { position },
        getLimit = { limit },
        getByte = { getByte(it) },
        handleLeadingZero = { handleLeadingZero() },
        parseLongDigits = { neg, start -> parseLongDigits(neg, start) },
        consumeNumericCoercionFooter = { consumeNumericCoercionFooter() }
    )

    private fun prepareNumericHeader(): Int {
        if (nextTokenByte == C.RESET_TOKEN_BYTE) { skipWhitespace() }
        if (position >= limit) { throwError(C.ERR_EXPECTED_NUMBER) }

        var header = 0
        var token = nextTokenByte

        if (token == C.QUOTE_INT) {
            if (!coerceStringsToNumbers) { throwError(C.ERR_COERCION_DISABLED) }
            position++
            nextTokenByte = C.RESET_TOKEN_BYTE
            skipWhitespace()
            if (position >= limit) { throwError(C.ERR_EXPECTED_NUMBER) }
            token = nextTokenByte
            header = header or C.NUMERIC_HEADER_QUOTED
        }

        if (token == C.MINUS_INT) {
            if (position + 1 >= limit) { throwError(C.ERR_ISOLATED_MINUS) }
            position++
            nextTokenByte = C.RESET_TOKEN_BYTE
            header = header or C.NUMERIC_HEADER_NEGATIVE
        }

        return header
    }

    private fun handleLeadingZero() {
        val nextCursor = position + 1
        if (nextCursor < limit) {
            val nextDigitByte = getByte(nextCursor)
            if ((C.DIGIT_BITMASK shr nextDigitByte) and C.BYTE_SHIFT_UNIT != C.RESULT_NONE) {
                throwError(C.ERR_LEADING_ZEROS)
            }
        }
        internalSkip(1)
    }

    private fun parseIntDigits(isNegative: Boolean, startOfNumber: Int): Int = parseIntDigitsImpl(
        isNegative = isNegative,
        startOfNumber = startOfNumber,
        readNumericLoop = { onDigit, onBreak -> readNumericLoop(onDigit, onBreak) },
        calculateIntWithOverflowCheck = { curr, digit, neg -> calculateIntWithOverflowCheck(curr, digit, neg) },
        isNumericSeparator = { isNumericSeparator(it) },
        setPosition = { position = it },
        nextDouble = { nextDouble() },
        throwError = { throwError(it) },
        setNextTokenByte = { nextTokenByte = it }
    )

    private fun parseLongDigits(isNegative: Boolean, startOfNumber: Int): Long = parseLongDigitsImpl(
        isNegative = isNegative,
        startOfNumber = startOfNumber,
        readNumericLoop = { onDigit, onBreak -> readNumericLoop(onDigit, onBreak) },
        calculateLongWithOverflowCheck = { curr, digit, neg -> calculateLongWithOverflowCheck(curr, digit, neg) },
        isNumericSeparator = { isNumericSeparator(it) },
        setPosition = { position = it },
        nextDouble = { nextDouble() },
        throwError = { throwError(it) },
        setNextTokenByte = { nextTokenByte = it }
    )

    @Suppress("NOTHING_TO_INLINE")
    private inline fun consumeNumericCoercionFooter() {
        if (position >= limit || getByte(position) != C.QUOTE_INT) {
            throwError(C.ERR_EXPECTED_COERCION_QUOTE)
        }
        internalSkip(1)
        skipWhitespace()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun calculateIntWithOverflowCheck(current: Int, digitValue: Int, isNegative: Boolean): Int {
        if (current > C.INT_OVERFLOW_LIMIT ||
            (current == C.INT_OVERFLOW_LIMIT && digitValue > (if (isNegative) C.INT_MIN_LAST_DIGIT else C.INT_MAX_LAST_DIGIT))
        ) {
            throwError(C.ERR_INT_OVERFLOW)
        }
        return current * C.BASE_TEN + digitValue
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun calculateLongWithOverflowCheck(current: Long, digitValue: Int, isNegative: Boolean): Long {
        if (current > C.LONG_OVERFLOW_LIMIT ||
            (current == C.LONG_OVERFLOW_LIMIT && digitValue > C.LONG_MAX_LAST_DIGIT)
        ) {
            if (isNegative && current == C.LONG_OVERFLOW_LIMIT && digitValue == C.LONG_MIN_LAST_DIGIT) {
                return Long.MIN_VALUE
            }
            throwError(C.ERR_LONG_OVERFLOW)
        }
        return current * C.BASE_TEN + digitValue
    }

    private fun validateLeadingZero() {
        if (position < limit && getByte(position) == C.ZERO_INT && position + 1 < limit) {
            val nextDigitByte = getByte(position + 1)
            if ((C.DIGIT_BITMASK shr nextDigitByte) and 1L != 0L) {
                throwError(C.ERR_LEADING_ZEROS)
            }
        }
    }

    private fun validateNumericRangeFloat(valueToValidate: Float) {
        if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
            throwError(C.ERR_NUMERIC_OVERFLOW)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isNumericSeparator(byteCode: Int): Boolean {
        return byteCode == C.DOT_INT || byteCode == C.EXP_LOWER_INT || byteCode == C.EXP_UPPER_INT
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isExponentMarker(markerByte: Int): Boolean {
        return (markerByte or C.CASE_INSENSITIVE_MASK) == C.EXP_LOWER_INT
    }

    private fun validateNumericRange(valueToValidate: Double) {
        if (valueToValidate.isInfinite() || valueToValidate.isNaN()) {
            throwError(C.ERR_NUMERIC_OVERFLOW)
        }
    }

    fun skipNumber() = skipNumberImpl(
        prepareNumericHeader = { prepareNumericHeader() },
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        getByte = { getByte(it) },
        isDigit = { isDigit(it) },
        readNumericLoop = { onDigit -> readNumericLoop(onDigit) },
        throwError = { throwError(it) },
        consumeNumericCoercionFooter = { consumeNumericCoercionFooter() },
        setNextTokenByte = { nextTokenByte = it }
    )

    private inline fun readNumericLoop(
        crossinline onDigit: (byte: Int) -> Unit,
        crossinline onBreak: (byte: Int) -> Unit = {}
    ) {
        val data = rawData
        val localLimit = limit
        while (position < localLimit) {
            val byte = data[position].toInt() and C.BYTE_MASK
            if (isDigit(byte)) {
                onDigit(byte)
                position++
            } else {
                onBreak(byte)
                break
            }
        }
    }

    companion object {
        @InternalGhostApi
        const val RESET_TOKEN_BYTE: Int = -1
    }
}
