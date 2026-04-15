package com.ghost.serialization.core

import com.ghost.serialization.core.GhostJsonConstants.CLOSE_ARR
import com.ghost.serialization.core.GhostJsonConstants.CLOSE_OBJ
import com.ghost.serialization.core.GhostJsonConstants.COLON
import com.ghost.serialization.core.GhostJsonConstants.COMMA
import com.ghost.serialization.core.GhostJsonConstants.FALSE_CHAR
import com.ghost.serialization.core.GhostJsonConstants.FALSE_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.NULL_CHAR
import com.ghost.serialization.core.GhostJsonConstants.NULL_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.OPEN_ARR
import com.ghost.serialization.core.GhostJsonConstants.OPEN_OBJ
import com.ghost.serialization.core.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.GhostJsonConstants.TRUE_CHAR
import com.ghost.serialization.core.GhostJsonConstants.TRUE_LENGTH

/**
 * Hardened Subsystem (V22).
 * Implements Atomic Match and Uniform Consumption for absolute integrity.
 */

fun GhostJsonReader.selectName(options: GhostJsonReader.Options): Int {
    skipWhitespace()
    if (!source.request(1)) internalThrowError("Unexpected EOF")
    var b = source.buffer[0]
    
    if (b == CLOSE_OBJ) return -1
    
    if (b == COMMA) {
        internalSkip(1)
        skipWhitespace()
        if (!source.request(1)) internalThrowError("Unexpected EOF")
        b = source.buffer[0]
        if (b == CLOSE_OBJ) return -1
    }
    
    if (b != QUOTE) internalThrowError("Expected property name but found ${b.toInt().toChar()}")
    internalSkip(1)

    val index = internalSelect(options)
    if (index != -1) {
        if (source.request(1) && source.buffer[0] == QUOTE) {
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
    if (!source.request(1)) {
        if (throwOnEof) internalThrowError("Unexpected EOF")
        return -1
    }
    return source.buffer[0]
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
    if (!source.request(1) || source.buffer[0] != COLON) {
        internalThrowError("Expected ':'")
    }
    internalSkip(1)
}

fun GhostJsonReader.consumeArraySeparator() {
    val b = nextNonWhitespace()
    if (b == COMMA) {
        internalSkip(1)
    } else if (b != CLOSE_ARR) {
        internalThrowError("Expected ',' but found ${b.toInt().toChar()}")
    }
}

fun GhostJsonReader.isNextNullValue(): Boolean = nextNonWhitespace() == NULL_CHAR

fun GhostJsonReader.consumeNull() { 
    internalSkip(NULL_LENGTH)
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
    if (!source.rangeEquals(0, expected)) {
        internalThrowError("Expected literal $expected but found ${source.readUtf8(expected.size.toLong())}")
    }
    val next = peekNextByte(expected.size.toLong())
    if (next != null && (next < 0 || next >= 128 || !GhostJsonConstants.IS_TERMINATOR[next.toInt()])) {
        internalThrowError("Unexpected character after literal: ${next.toInt().toChar()}")
    }
    internalSkip(expected.size.toLong())
}

fun GhostJsonReader.skipValue() {
    skipWhitespace()
    skipAnyValue()
}

fun <T> GhostJsonReader.readList(capacity: Int = 10, itemParser: () -> T): List<T> {
    beginArray()
    skipWhitespace()
    if (source.request(1) && source.buffer[0] == CLOSE_ARR) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(capacity)
    list.add(itemParser())
    while (true) {
        skipWhitespace()
        if (!source.request(1)) internalThrowError("Unexpected EOF in array")
        val b = source.buffer[0]
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
    while (balance > 0) {
        if (depth + balance > maxDepth) {
            internalThrowError("Reached maximum recursion depth ($maxDepth) during skip")
        }
        val b = internalReadByte()
        if (b == open) balance++
        else if (b == close) balance--
        else if (b == QUOTE) skipQuotedStringBody()
    }
}

fun GhostJsonReader.peekStringField(keyName: String): String? {
    val peekSource = source.peek()
    var b = 0.toByte()
    while (peekSource.request(1)) {
        b = peekSource.buffer[0]
        if (b > 32) break
        peekSource.skip(1)
    }
    
    if (b != OPEN_OBJ) return null
    peekSource.skip(1) // Skip '{'
    
    val tempReader = GhostJsonReader(peekSource, maxDepth = this.maxDepth, strictMode = false)
    try {
        while (true) {
            val currentKey = tempReader.nextKey() ?: break
            tempReader.consumeKeySeparator()
            if (currentKey == keyName) {
                return tempReader.nextString()
            }
            tempReader.skipValue()
            tempReader.skipCommaIfPresent()
        }
    } catch (e: Exception) {
    } finally {
        peekSource.close()
    }
    return null
}
