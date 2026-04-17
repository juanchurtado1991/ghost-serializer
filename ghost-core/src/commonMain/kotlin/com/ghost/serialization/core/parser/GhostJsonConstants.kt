package com.ghost.serialization.core.parser

import okio.ByteString.Companion.encodeUtf8
import kotlin.math.pow

@PublishedApi
internal object GhostJsonConstants {
    const val STRING_BUILDER_CAPACITY = 64
    const val UNTERMINATED_STRING_ERROR = "Unterminated string"
    const val TRUNCATED_LITERAL_ERROR = "Truncated literal at end of source"
    const val UNEXPECTED_EOF_ERROR = "Unexpected EOF"
    const val UNTERMINATED_ESCAPE_ERROR = "Unterminated escape sequence"
    const val UNTERMINATED_UNICODE_ERROR = "Unterminated unicode escape"
    const val UNESCAPED_CONTROL_CHAR_ERROR = "Unescaped control character in string"
    const val STRICT_MODE_UNKNOWN_FIELD = "Unknown field in strict mode: "
    const val PATH_ROOT = "$"
    const val COLON_QUOTE = "\":"
    const val UNICODE_PREFIX = "\\u"
    const val ZERO_CHAR = "0"
    const val ERR_NON_FINITE = "JSON does not support non-finite numbers like NaN or Infinity"
    const val ERR_DEPTH_EXCEEDED = "Reached maximum recursion depth"
    const val JAVA_MAP = "java.util.Map"
    const val KOTLIN_MAP = "kotlin.collections.Map"
    const val KOTLIN_LIST = "kotlin.collections.List"
    const val JAVA_LIST = "java.util.List"
    const val STRING = "String"
    const val INT = "Int"
    const val LONG = "Long"
    const val BOOLEAN = "Boolean"
    const val DOUBLE = "Double"

    @PublishedApi
    internal val TRUE_BYTES = "true".encodeUtf8()
    @PublishedApi
    internal val FALSE_BYTES = "false".encodeUtf8()
    @PublishedApi
    internal val NULL_BYTES = "null".encodeUtf8()

    const val NEWLINE = '\n'.code.toByte()

    const val COMMA = ','.code.toByte()
    const val COLON = ':'.code.toByte()
    const val QUOTE = '"'.code.toByte()
    const val BACKSLASH = '\\'.code.toByte()
    const val OPEN_OBJ = '{'.code.toByte()
    const val CLOSE_OBJ = '}'.code.toByte()
    const val OPEN_ARR = '['.code.toByte()
    const val CLOSE_ARR = ']'.code.toByte()
    const val NULL_CHAR = 'n'.code.toByte()
    const val TRUE_CHAR = 't'.code.toByte()
    const val FALSE_CHAR = 'f'.code.toByte()
    const val MINUS = '-'.code.toByte()
    const val PLUS = '+'.code.toByte()
    const val DOT = '.'.code.toByte()
    const val EXP_LOWER = 'e'.code.toByte()
    const val EXP_UPPER = 'E'.code.toByte()

    // --- STR POOL METRICS ---
    const val STR_POOL_SIZE = 16384
    const val MAX_POOL_STRING_LENGTH = 64

    val POWERS_OF_TEN = DoubleArray(309).apply {
        for (i in indices) this[i] = 10.0.pow(i.toDouble())
    }

    val INVERSE_POWERS_OF_TEN = DoubleArray(309).apply {
        for (i in indices) this[i] = 1.0 / 10.0.pow(i.toDouble())
    }

    val BLOCK_ESCAPE = ByteArray(128).apply {
        for (i in 0..31) this[i] = 1 // Control characters
        this['"'.code] = 1
        this['\\'.code] = 1
    }

    val IS_TERMINATOR = BooleanArray(128).apply {
        ",}] \n\r\t:".forEach { this[it.code] = true }
    }

    val IS_STRING_TERMINATOR = BooleanArray(256).apply {
        for (i in 0..31) this[i] = true
        this['"'.code] = true
        this['\\'.code] = true
    }
}
