package com.ghost.serialization.core

import com.ghost.serialization.core.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.core.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.core.GhostJsonConstants.COLON
import com.ghost.serialization.core.GhostJsonConstants.COMMA
import com.ghost.serialization.core.GhostJsonConstants.FALSE_CHAR
import com.ghost.serialization.core.GhostJsonConstants.NULL_CHAR
import com.ghost.serialization.core.GhostJsonConstants.NULL_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.OPEN_ARR
import com.ghost.serialization.core.GhostJsonConstants.OPEN_OBJ
import com.ghost.serialization.core.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.GhostJsonConstants.TRUE_CHAR

fun GhostJsonReader.selectName(options: GhostJsonReader.Options): Int {
    skipWhitespace()
    if (pos >= data.size) internalThrowError("Unexpected EOF")
    var b = data[pos]

    if (b == CLOSE_OBJ) return -1

    if (b == COMMA) {
        internalSkip(1)
        skipWhitespace()
        if (pos >= data.size) internalThrowError("Unexpected EOF")
        b = data[pos]
        if (b == CLOSE_OBJ) return -1
    }

    if (b != QUOTE) internalThrowError("Expected property name but found ${b.toInt().toChar()}")
    internalSkip(1)

    val index = internalSelect(options)
    if (index != -1) {
        if (pos < data.size && data[pos] == QUOTE) {
            internalSkip(1)
            return index
        }
        val remainder = readStringBody()
        val fullName = options.strings[index] + remainder
        for (i in options.strings.indices) {
            if (options.strings[i] == fullName) return i
        }
    } else {
        skipQuotedStringBody()
    }

    if (strictMode) internalThrowError("Unexpected field in strict mode")
    return -2
}

internal fun GhostJsonReader.nextNonWhitespace(throwOnEof: Boolean = true): Byte {
    skipWhitespace()
    if (pos >= data.size) {
        if (throwOnEof) internalThrowError("Unexpected EOF")
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
        if (b == CLOSE_OBJ) internalThrowError("Trailing comma not allowed in object")
    } else if (b == CLOSE_OBJ) return null

    return readQuotedString()
}

fun GhostJsonReader.consumeKeySeparator() {
    skipWhitespace()
    if (pos >= data.size || data[pos] != COLON) {
        internalThrowError("Expected ':'")
    }
    internalSkip(1)
}

fun GhostJsonReader.consumeArraySeparator() {
    skipWhitespace()
    if (pos >= data.size) internalThrowError("Unexpected EOF")
    val b = data[pos]
    if (b == COMMA) {
        internalSkip(1)
    } else if (b != CLOSE_ARR) {
        internalThrowError("Expected ',' but found ${b.toInt().toChar()}")
    }
}

fun GhostJsonReader.isNextNullValue(): Boolean = nextNonWhitespace() == NULL_CHAR

fun GhostJsonReader.consumeNull() {
    val len = NULL_LENGTH.toInt()
    if (pos + len > data.size) internalThrowError("Truncated 'null' literal")
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
    if (pos + len > data.size) internalThrowError("Unexpected EOF during literal")
    for (i in 0 until len) {
        if (data[pos + i] != expected[i]) {
            internalThrowError("Expected literal but found mismatch")
        }
    }
    val afterPos = pos + len
    if (afterPos < data.size) {
        val next = data[afterPos]
        val code = next.toInt() and 0xFF
        if (code < 128 && !GhostJsonConstants.IS_TERMINATOR[code]) {
            internalThrowError("Unexpected character after literal: ${next.toInt().toChar()}")
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
        if (pos >= data.size) internalThrowError("Unexpected EOF in array")
        val b = data[pos]
        if (b == CLOSE_ARR) break
        if (b != COMMA) internalThrowError("Expected ',' or ']' but found ${b.toInt().toChar()}")
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
            internalThrowError("Reached maximum recursion depth ($maxDepth) during skip")
        }
        val b = data[pos++]; column++
        if (b == open) balance++
        else if (b == close) balance--
        else if (b == QUOTE) skipQuotedStringBody()
    }
    if (balance > 0) internalThrowError("Unexpected EOF during balanced skip")
}

fun GhostJsonReader.peekStringField(keyName: String): String? {
    val savedPos = pos
    val savedLine = line
    val savedColumn = column
    try {
        skipWhitespace()
        if (pos >= data.size || data[pos] != OPEN_OBJ) return null
        pos++; column++

        val tempReader = GhostJsonReader(data, maxDepth = this.maxDepth, strictMode = false)
        tempReader.pos = pos
        tempReader.line = line
        tempReader.column = column

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
        line = savedLine
        column = savedColumn
    }
    return null
}
