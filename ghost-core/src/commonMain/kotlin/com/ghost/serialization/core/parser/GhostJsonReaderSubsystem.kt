package com.ghost.serialization.core.parser

import com.ghost.serialization.core.parser.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.core.parser.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.core.parser.GhostJsonConstants.COLON
import com.ghost.serialization.core.parser.GhostJsonConstants.COMMA
import com.ghost.serialization.core.parser.GhostJsonConstants.FALSE_CHAR
import com.ghost.serialization.core.parser.GhostJsonConstants.NULL_CHAR
import com.ghost.serialization.core.parser.GhostJsonConstants.OPEN_ARR
import com.ghost.serialization.core.parser.GhostJsonConstants.OPEN_OBJ
import com.ghost.serialization.core.parser.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.parser.GhostJsonConstants.TRUE_CHAR


/**
 * Pre-scans the next available non-whitespace token without consuming it.
 */
internal fun GhostJsonReader.peekNextToken(): Int {
    if (nextTokenByte != -1) return nextTokenByte
    skipWhitespace()
    if (positon >= data.size) return -1
    val tokenByte = data[positon].toInt() and 0xFF
    nextTokenByte = tokenByte
    return tokenByte
}

/**
 * Consumes and returns the next available non-whitespace byte.
 */
internal fun GhostJsonReader.nextNonWhitespace(throwOnEof: Boolean = true): Byte {
    val nonWhitespaceByte = peekNextToken()
    if (nonWhitespaceByte == -1 && throwOnEof) throwError("Unexpected EOF")
    return nonWhitespaceByte.toByte()
}

/**
 * Matches the next field key in a JSON object. Returns null if the object ends.
 */
fun GhostJsonReader.nextKey(): String? {
    var keyStartByte = nextNonWhitespace()
    if (keyStartByte == CLOSE_OBJ) return null
    if (keyStartByte == COMMA) {
        internalSkip(1)
        keyStartByte = nextNonWhitespace()
        if (keyStartByte == CLOSE_OBJ) throwError("Trailing comma not allowed in object")
    } else if (keyStartByte == CLOSE_OBJ) return null

    return readQuotedString()
}

/** Consumes the ':' separator and pre-scans the next value. */
fun GhostJsonReader.consumeKeySeparator() {
    val separatorByte = peekNextToken()
    if (separatorByte != COLON.toInt()) throwError("Expected ':' but found ${separatorByte.toChar()}")
    internalSkip(1)
    peekNextToken() // Pre-scan the next value token
}

/** Consumes the ',' array separator. */
fun GhostJsonReader.consumeArraySeparator() {
    skipWhitespace()
    if (positon >= data.size) throwError("Unexpected EOF")
    val arraySeparatorByte = data[positon]
    if (arraySeparatorByte == COMMA) {
        internalSkip(1)
    } else if (arraySeparatorByte != CLOSE_ARR) {
        throwError("Expected ',' but found ${arraySeparatorByte.toInt().toChar()}")
    }
}

/** Returns true if the next token matches a JSON 'null' literal. */
fun GhostJsonReader.isNextNullValue(): Boolean = peekNextToken() == NULL_CHAR.toInt()

/** Consumes a JSON 'null' literal. */
fun GhostJsonReader.consumeNull() {
    val length = GhostJsonConstants.NULL_BYTES.size
    if (positon + length > data.size) throwError("Truncated 'null' literal")
    internalSkip(length)
}

/**
 * Skips the next available JSON value, regardless of its type.
 */
fun GhostJsonReader.skipAnyValue() {
    when (peekByte()) {
        QUOTE -> skipQuotedString()
        OPEN_OBJ -> {
            internalSkip(1); skipBalanced(OPEN_OBJ, CLOSE_OBJ)
        }

        OPEN_ARR -> {
            internalSkip(1); skipBalanced(OPEN_ARR, CLOSE_ARR)
        }

        TRUE_CHAR -> skipAndValidateLiteral(GhostJsonConstants.TRUE_BYTES)
        FALSE_CHAR -> skipAndValidateLiteral(GhostJsonConstants.FALSE_BYTES)
        NULL_CHAR -> skipAndValidateLiteral(GhostJsonConstants.NULL_BYTES)
        else -> skipRawNumber()
    }
}

internal fun GhostJsonReader.skipAndValidateLiteral(expected: okio.ByteString) {
    val length = expected.size
    if (positon + length > data.size) throwError("Unexpected EOF during literal")
    for (i in 0 until length) {
        if (data[positon + i] != expected[i]) {
            throwError("Expected literal but found mismatch")
        }
    }
    val afterPos = positon + length
    if (afterPos < data.size) {
        val nextByte = data[afterPos]
        val byteCode = nextByte.toInt() and 0xFF
        if (byteCode < 128 && !GhostJsonConstants.IS_TERMINATOR[byteCode]) {
            throwError("Unexpected character after literal: ${nextByte.toInt().toChar()}")
        }
    }
    internalSkip(length)
}

/**
 * Skips the next JSON value, ensuring leading whitespace is ignored.
 */
fun GhostJsonReader.skipValue() {
    skipWhitespace()
    skipAnyValue()
}

/**
 * Reads a JSON array into a [List] using the provided [itemParser].
 *
 * @param capacity Initial capacity for the result list.
 * @param itemParser Lambda to parse individual items.
 */
fun <T> GhostJsonReader.readList(capacity: Int = 1024, itemParser: () -> T): List<T> {
    beginArray()
    if (peekNextToken() == CLOSE_ARR.toInt()) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(capacity)
    list.add(itemParser())
    while (true) {
        val nextToken = peekNextToken()
        if (nextToken == -1) throwError("Unexpected EOF in array")
        if (nextToken.toByte() == CLOSE_ARR) break
        if (nextToken.toByte() != COMMA) throwError("Expected ',' or ']' but found ${nextToken.toChar()}")
        internalSkip(1)
        list.add(itemParser())
    }
    endArray()
    return list
}

fun GhostJsonReader.peekJsonToken(): JsonToken {
    val tokenByte = nextNonWhitespace(throwOnEof = false)
    if (tokenByte == (-1).toByte()) return JsonToken.END_DOCUMENT
    return when (tokenByte) {
        OPEN_OBJ -> JsonToken.BEGIN_OBJECT
        CLOSE_OBJ -> JsonToken.END_OBJECT
        OPEN_ARR -> JsonToken.BEGIN_ARRAY
        CLOSE_ARR -> JsonToken.END_ARRAY
        QUOTE -> JsonToken.STRING
        NULL_CHAR -> JsonToken.NULL
        TRUE_CHAR, FALSE_CHAR -> JsonToken.BOOLEAN
        else -> JsonToken.NUMBER
    }
}

internal fun GhostJsonReader.skipCommaIfPresent() {
    if (nextNonWhitespace(throwOnEof = false) == COMMA) {
        internalSkip(1)
    }
}

internal fun GhostJsonReader.skipBalanced(open: Byte, close: Byte) {
    var balance = 1
    while (balance > 0 && positon < data.size) {
        if (depth + balance > maxDepth) {
            throwError("Reached maximum recursion depth ($maxDepth) during skip")
        }
        val currentByte = data[positon]
        internalSkip(1)
        if (currentByte == open) balance++
        else if (currentByte == close) balance--
        else if (currentByte == QUOTE) skipQuotedStringBody()
    }
    if (balance > 0) throwError("Unexpected EOF during balanced skip")
}

/**
 * Peeks a specific string field from the current JSON object without
 * advancing the main reader position.
 *
 * @param keyName The name of the field to look for.
 * @return The string value if found, or null otherwise.
 */
fun GhostJsonReader.peekStringField(keyName: String): String? {
    val savedPos = positon
    try {
        skipWhitespace()
        if (positon >= data.size || data[positon] != OPEN_OBJ) return null
        internalSkip(1)

        val tempReader = GhostJsonReader(data, maxDepth = this.maxDepth, strictMode = false)
        tempReader.positon = positon

        while (true) {
            val currentKey = tempReader.nextKey() ?: break
            tempReader.consumeKeySeparator()
            if (currentKey == keyName) return tempReader.nextString()
            tempReader.skipValue()
            tempReader.skipCommaIfPresent()
        }
    } catch (_: Exception) {
    } finally {
        positon = savedPos
    }
    return null
}
