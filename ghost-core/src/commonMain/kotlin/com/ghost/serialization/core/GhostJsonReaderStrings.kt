package com.ghost.serialization.core

import com.ghost.serialization.core.GhostJsonConstants.BACKSLASH
import com.ghost.serialization.core.GhostJsonConstants.QUOTE
import com.ghost.serialization.core.GhostJsonConstants.HEX_RADIX
import com.ghost.serialization.core.GhostJsonConstants.UNICODE_HEX_LENGTH
import com.ghost.serialization.core.GhostJsonConstants.UNTERMINATED_STRING_ERROR

/**
 * String parsing extensions for GhostJsonReader.
 */

internal fun GhostJsonReader.readStringWithEscapes(initialLength: Long): String {
    val builder = StringBuilder(GhostJsonConstants.STRING_BUILDER_CAPACITY)
    builder.append(source.readUtf8(initialLength))
    column += initialLength.toInt()
    
    while (!source.exhausted()) {
        expectByte(BACKSLASH)
        readAndAppendEscapedChar(builder)
        
        val nextBackslash = source.indexOf(BACKSLASH)
        val nextQuote = source.indexOf(QUOTE)
        
        if (nextQuote == -1L) internalThrowError(UNTERMINATED_STRING_ERROR)
        
        if (nextBackslash == -1L || nextQuote < nextBackslash) {
            builder.append(source.readUtf8(nextQuote))
            source.readByte()
            column += (nextQuote + 1).toInt()
            return builder.toString()
        }
        
        builder.append(source.readUtf8(nextBackslash))
        column += nextBackslash.toInt()
    }
    internalThrowError(UNTERMINATED_STRING_ERROR)
}

private fun GhostJsonReader.readAndAppendEscapedChar(builder: StringBuilder) {
    val escaped = source.readByte().toInt().toChar()
    when (escaped) {
        '"' -> builder.append('"')
        '\\' -> builder.append('\\')
        '/' -> builder.append('/')
        'b' -> builder.append('\b')
        'f' -> builder.append('\u000C')
        'n' -> builder.append('\n')
        'r' -> builder.append('\r')
        't' -> builder.append('\t')
        'u' -> {
            val first = readUnicodeEscape()
            if (first.isHighSurrogate()) {
                if (source.request(2) && source.buffer[0] == GhostJsonConstants.BACKSLASH && source.buffer[1] == 'u'.code.toByte()) {
                    source.skip(2)
                    column += 2
                    val second = readUnicodeEscape()
                    if (second.isLowSurrogate()) {
                        builder.append(first)
                        builder.append(second)
                    } else {
                        internalThrowError("Expected low surrogate after high surrogate")
                    }
                } else {
                    // Isolated high surrogate - industrial policy: throw or append? 
                    // JSON spec says isolation is not allowed.
                    internalThrowError("Isolated high surrogate")
                }
            } else if (first.isLowSurrogate()) {
                internalThrowError("Isolated low surrogate")
            } else {
                builder.append(first)
            }
        }
        else -> builder.append(escaped)
    }
}

internal fun GhostJsonReader.readUnicodeEscape(): Char {
    if (!source.request(UNICODE_HEX_LENGTH)) internalThrowError(UNTERMINATED_STRING_ERROR)
    val hex = source.readUtf8(UNICODE_HEX_LENGTH)
    try {
        return hex.toInt(HEX_RADIX).toChar()
    } catch (e: Exception) {
        internalThrowError("Invalid unicode escape: \\u$hex")
    }
}

internal fun GhostJsonReader.skipQuotedString() {
    expectByte(QUOTE)
    skipQuotedStringBody()
}

internal fun GhostJsonReader.skipQuotedStringBody() {
    while (true) {
        val index = source.indexOfElement(GhostJsonConstants.QUOTE_OR_BACKSLASH)
        if (index == -1L) internalThrowError(UNTERMINATED_STRING_ERROR)
        val b = source.buffer[index]
        if (b == QUOTE) {
            source.skip(index + 1)
            column += (index + 1).toInt()
            return
        }
        // Escaped sequence
        if (b != BACKSLASH) {
            internalThrowError("Unescaped control character in string: 0x${b.toInt().toString(GhostJsonConstants.HEX_RADIX)}")
        }
        source.skip(index + 1) // skip to after the backslash
        column += (index + 1).toInt()
        val escaped = source.readByte()
        column++
        if (escaped == 'u'.code.toByte()) {
            readUnicodeEscape()
        }
    }
}
