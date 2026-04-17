package com.ghost.serialization.core.parser

import com.ghost.serialization.core.parser.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.core.parser.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.core.parser.GhostJsonConstants.COLON
import com.ghost.serialization.core.parser.GhostJsonConstants.COMMA
import com.ghost.serialization.core.parser.GhostJsonConstants.FALSE_CHAR
import com.ghost.serialization.core.parser.GhostJsonConstants.NULL_CHAR
import com.ghost.serialization.core.parser.GhostJsonConstants.NULL_LENGTH
import com.ghost.serialization.core.parser.GhostJsonConstants.OPEN_ARR
import com.ghost.serialization.core.parser.GhostJsonConstants.OPEN_OBJ
import com.ghost.serialization.core.parser.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.parser.GhostJsonConstants.TRUE_CHAR


internal fun GhostJsonReader.nextNonWhitespace(throwOnEof: Boolean = true): Byte {
    skipWhitespace()
    if (pos >= data.size) {
        if (throwOnEof) throwError("Unexpected EOF")
        return -1
    }
    return data[pos]
}

fun GhostJsonReader.nextKey(): String? {
    var b = nextNonWhitespace()
    if (b == CLOSE_OBJ) return null
    if (b == COMMA) {
        internalSkip(1)
        b = nextNonWhitespace()
        if (b == CLOSE_OBJ) throwError("Trailing comma not allowed in object")
    } else if (b == CLOSE_OBJ) return null

    return readQuotedString()
}

fun GhostJsonReader.consumeKeySeparator() {
    skipWhitespace()
    if (pos >= data.size || data[pos] != COLON) {
        throwError("Expected ':'")
    }
    internalSkip(1)
}

fun GhostJsonReader.consumeArraySeparator() {
    skipWhitespace()
    if (pos >= data.size) throwError("Unexpected EOF")
    val b = data[pos]
    if (b == COMMA) {
        internalSkip(1)
    } else if (b != CLOSE_ARR) {
        throwError("Expected ',' but found ${b.toInt().toChar()}")
    }
}

fun GhostJsonReader.isNextNullValue(): Boolean = nextNonWhitespace() == NULL_CHAR

fun GhostJsonReader.consumeNull() {
    val len = NULL_LENGTH.toInt()
    if (pos + len > data.size) throwError("Truncated 'null' literal")
    internalSkip(len)
}

fun GhostJsonReader.skipAnyValue() {
    when (peekByte()) {
        QUOTE -> skipQuotedString()
        OPEN_OBJ -> { internalSkip(1); skipBalanced(OPEN_OBJ, CLOSE_OBJ) }
        OPEN_ARR -> { internalSkip(1); skipBalanced(OPEN_ARR, CLOSE_ARR) }
        TRUE_CHAR -> skipAndValidateLiteral(GhostJsonConstants.TRUE_BYTES)
        FALSE_CHAR -> skipAndValidateLiteral(GhostJsonConstants.FALSE_BYTES)
        NULL_CHAR -> skipAndValidateLiteral(GhostJsonConstants.NULL_BYTES)
        else -> skipRawNumber()
    }
}

internal fun GhostJsonReader.skipAndValidateLiteral(expected: okio.ByteString) {
    val len = expected.size
    if (pos + len > data.size) throwError("Unexpected EOF during literal")
    for (i in 0 until len) {
        if (data[pos + i] != expected[i]) {
            throwError("Expected literal but found mismatch")
        }
    }
    val afterPos = pos + len
    if (afterPos < data.size) {
        val next = data[afterPos]
        val code = next.toInt() and 0xFF
        if (code < 128 && !GhostJsonConstants.IS_TERMINATOR[code]) {
            throwError("Unexpected character after literal: ${next.toInt().toChar()}")
        }
    }
    internalSkip(len)
}

fun GhostJsonReader.skipValue() {
    skipWhitespace()
    skipAnyValue()
}

fun <T> GhostJsonReader.readList(capacity: Int = 10, itemParser: () -> T): List<T> {
    beginArray()
    skipWhitespace()
    if (pos < data.size && data[pos] == CLOSE_ARR) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(capacity)
    list.add(itemParser())
    while (true) {
        skipWhitespace()
        if (pos >= data.size) throwError("Unexpected EOF in array")
        val b = data[pos]
        if (b == CLOSE_ARR) break
        if (b != COMMA) throwError("Expected ',' or ']' but found ${b.toInt().toChar()}")
        internalSkip(1)
        list.add(itemParser())
    }
    endArray()
    return list
}

fun GhostJsonReader.peekJsonToken(): JsonToken {
    val b = nextNonWhitespace(throwOnEof = false)
    if (b == (-1).toByte()) return JsonToken.END_DOCUMENT
    return when (b) {
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
    while (balance > 0 && pos < data.size) {
        if (depth + balance > maxDepth) {
            throwError("Reached maximum recursion depth ($maxDepth) during skip")
        }
        val b = data[pos++]
        if (b == open) balance++
        else if (b == close) balance--
        else if (b == QUOTE) skipQuotedStringBody()
    }
    if (balance > 0) throwError("Unexpected EOF during balanced skip")
}

fun GhostJsonReader.peekStringField(keyName: String): String? {
    val savedPos = pos
    try {
        skipWhitespace()
        if (pos >= data.size || data[pos] != OPEN_OBJ) return null
        pos++

        val tempReader = GhostJsonReader(data, maxDepth = this.maxDepth, strictMode = false)
        tempReader.pos = pos

        while (true) {
            val currentKey = tempReader.nextKey() ?: break
            tempReader.consumeKeySeparator()
            if (currentKey == keyName) return tempReader.nextString()
            tempReader.skipValue()
            tempReader.skipCommaIfPresent()
        }
    } catch (_: Exception) {
    } finally {
        pos = savedPos
    }
    return null
}
