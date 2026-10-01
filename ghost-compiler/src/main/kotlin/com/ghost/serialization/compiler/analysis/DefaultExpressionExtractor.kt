package com.ghost.serialization.compiler.analysis

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC


/**
 * Extracts constructor-parameter default expressions from source text, since KSP only exposes
 * [com.google.devtools.ksp.symbol.KSValueParameter.hasDefault], never the expression itself.
 * Accepts the RHS only when it matches a strict literal whitelist; anything else returns `null`
 * so callers fall back to `.copy()`.
 */
internal object DefaultExpressionExtractor {

    /**
     * Returns a whitelisted default expression for [param], or `null` when the source is
     * unavailable or the expression is not a safe literal.
     */
    fun extract(param: KSValueParameter): String? {
        if (!param.hasDefault) return null
        val name = param.name?.asString() ?: return null
        val location = param.location as? FileLocation ?: return null
        val file = File(location.filePath)
        if (!file.isFile) return null
        val source = runCatching { file.readText() }.getOrNull() ?: return null
        val raw = extractRawDefault(source = source, paramName = name, lineNumber = location.lineNumber) ?: return null
        return whitelist(expr = raw)
    }

    /**
     * Isolates the raw default RHS for [paramName] near [lineNumber] (1-based) without
     * whitelist filtering. Visible for unit tests.
     */
    internal fun extractRawDefault(source: String, paramName: String, lineNumber: Int): String? {
        val startOffset = offsetOfLine(source = source, lineNumber = lineNumber) ?: return null
        val paramStart = findParameterName(source = source, paramName = paramName, from = startOffset) ?: return null
        val eqIndex = findDefaultEquals(source = source, from = paramStart + paramName.length) ?: return null
        return readExpression(source = source, from = eqIndex + 1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns [expr] unchanged when it is a whitelisted literal; otherwise `null`.
     */
    internal fun whitelist(expr: String): String? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed in AC.DEFAULT_EXPR_SCALAR_KEYWORDS -> trimmed
            isNumericLiteral(value = trimmed) -> trimmed
            isCharLiteral(value = trimmed) -> trimmed
            isStringLiteral(value = trimmed) -> trimmed
            isEmptyCollectionCall(value = trimmed) -> trimmed
            isEnumOrConstRef(value = trimmed) -> trimmed
            else -> null
        }
    }

    /**
     * After the parameter name, skip the type (and annotations) until the default `=` at depth 0.
     */
    private fun findDefaultEquals(source: String, from: Int): Int? {
        var index = from
        var angle = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (index < source.length) {
            index = skipTrivia(source = source, from = index)
            if (index >= source.length) return null
            when (source[index]) {
                CHAR_DOUBLE_QUOTE, CHAR_SINGLE_QUOTE -> index = skipLiteral(source = source, from = index)
                CHAR_ANGLE_OPEN -> {
                    angle++; index++
                }

                CHAR_ANGLE_CLOSE -> {
                    angle = (angle - 1).coerceAtLeast(0); index++
                }

                CHAR_PAREN_OPEN -> {
                    paren++; index++
                }

                CHAR_PAREN_CLOSE -> {
                    val isAtTopLevel = paren == 0 && angle == 0 && bracket == 0 && brace == 0
                    if (isAtTopLevel) return null
                    paren = (paren - 1).coerceAtLeast(0)
                    index++
                }

                CHAR_BRACKET_OPEN -> {
                    bracket++; index++
                }

                CHAR_BRACKET_CLOSE -> {
                    bracket = (bracket - 1).coerceAtLeast(0); index++
                }

                CHAR_BRACE_OPEN -> {
                    brace++; index++
                }

                CHAR_BRACE_CLOSE -> {
                    brace = (brace - 1).coerceAtLeast(0); index++
                }

                CHAR_EQUALS -> {
                    val isAtTopLevel = angle == 0 && paren == 0 && bracket == 0 && brace == 0
                    if (isAtTopLevel) {
                        // Reject `==` / `!=` / `*=` etc. — default uses a single `=`.
                        val prev = source.getOrNull(index - 1)
                        val next = source.getOrNull(index + 1)
                        val isAssignmentEquals = next != CHAR_EQUALS &&
                            prev != CHAR_BANG &&
                            prev != CHAR_ANGLE_OPEN &&
                            prev != CHAR_ANGLE_CLOSE &&
                            prev != CHAR_COLON
                        if (isAssignmentEquals) {
                            return index
                        }
                    }
                    index++
                }

                CHAR_COMMA -> {
                    val isAtTopLevel = angle == 0 && paren == 0 && bracket == 0 && brace == 0
                    if (isAtTopLevel) return null
                    index++
                }

                else -> index++
            }
        }
        return null
    }

    /**
     * Finds [paramName] as a constructor parameter identifier at or after [from],
     * skipping string/char literals and comments.
     */
    private fun findParameterName(source: String, paramName: String, from: Int): Int? {
        var index = from.coerceAtLeast(0)
        // Search a bounded window: parameter declarations rarely span far past location.
        val end = (from + AC.DEFAULT_EXPR_MAX_PARAM_SEARCH_CHARS).coerceAtMost(source.length)
        while (index < end) {
            index = skipTrivia(source = source, from = index)
            if (index >= end) break
            when (val ch = source[index]) {
                CHAR_DOUBLE_QUOTE, CHAR_SINGLE_QUOTE -> index = skipLiteral(source = source, from = index)
                CHAR_SLASH -> {
                    val next = skipTrivia(source = source, from = index)
                    if (next == index) index++ else index = next
                }

                else -> {
                    val isParamNameAtIndex = ch.isJavaIdentifierStart() &&
                        matchesIdentifier(source = source, index = index, name = paramName)
                    if (isParamNameAtIndex) {
                        val after = index + paramName.length
                        // Parameter binder form: name followed by ':' — rejects receiver/call
                        // sites like `name.` or `name(`.
                        if (after < source.length) {
                            val nextSignificant = skipTrivia(source = source, from = after)
                            if (nextSignificant < source.length) {
                                val nextChar = source[nextSignificant]
                                if (nextChar == CHAR_COLON) return index
                            }
                        }
                    }
                    if (ch.isJavaIdentifierStart()) {
                        index++
                        while (index < end && source[index].isJavaIdentifierPart()) index++
                    } else {
                        index++
                    }
                }
            }
        }
        // Fallback: the location may have pointed at an annotation above the parameter.
        if (from > 0) {
            val back = (from - AC.DEFAULT_EXPR_MAX_PARAM_BACKTRACK_CHARS).coerceAtLeast(0)
            return findParameterName(source = source, paramName = paramName, from = back)?.takeIf { it >= back }
                ?: findParameterNameFromConstructor(source = source, paramName = paramName)
        }
        return findParameterNameFromConstructor(source = source, paramName = paramName)
    }

    private fun findParameterNameFromConstructor(source: String, paramName: String): Int? {
        // Last-resort scan: any `paramName:` that precedes an `=` before the next top-level comma/')'.
        var index = 0
        while (index < source.length) {
            index = skipTrivia(source = source, from = index)
            if (index >= source.length) break
            val isParamNameAtIndex = source[index].isJavaIdentifierStart() &&
                matchesIdentifier(source = source, index = index, name = paramName)
            if (isParamNameAtIndex) {
                val afterName = skipTrivia(source = source, from = index + paramName.length)
                if (afterName < source.length && source[afterName] == CHAR_COLON) {
                    val eq = findDefaultEquals(source = source, from = afterName + 1)
                    if (eq != null) return index
                }
                index += paramName.length
            } else if (source[index] == CHAR_DOUBLE_QUOTE || source[index] == CHAR_SINGLE_QUOTE) {
                index = skipLiteral(source = source, from = index)
            } else {
                index++
            }
        }
        return null
    }

    private fun isCharLiteral(value: String): Boolean {
        val isMalformedCharLiteral = value.length < AC.DEFAULT_EXPR_MIN_CHAR_LITERAL_LEN ||
            value.first() != CHAR_SINGLE_QUOTE ||
            value.last() != CHAR_SINGLE_QUOTE
        if (isMalformedCharLiteral) {
            return false
        }
        val inner = value.substring(1, value.length - 1)
        return when {
            inner.length == 1 && inner[0] != CHAR_BACKSLASH && inner[0] != CHAR_SINGLE_QUOTE -> true
            inner in AC.DEFAULT_EXPR_CHAR_ESCAPES -> true
            inner.startsWith(AC.STR_UNICODE_ESC_PREFIX) &&
                    inner.length == AC.DEFAULT_EXPR_UNICODE_ESC_LEN &&
                    isHexDigits(value = inner.substring(2)) -> true

            else -> false
        }
    }

    private fun isEmptyCollectionCall(value: String): Boolean {
        return value in AC.DEFAULT_EXPR_EMPTY_COLLECTION_CALLS
    }

    private fun isEnumOrConstRef(value: String): Boolean {
        // Qual.Name or Name only — reject lowercase-only identifiers (likely variables).
        if (!ENUM_REF_REGEX.matches(value)) return false
        val parts = value.split(CHAR_DOT)
        return parts.all { part ->
            part.first().isUpperCase() ||
                    part.all { it == CHAR_UNDERSCORE || it.isDigit() || it.isUpperCase() }
        }
    }

    private fun isHexDigits(value: String): Boolean =
        value.length == AC.DEFAULT_EXPR_UNICODE_HEX_LEN &&
                value.all { it.isDigit() || it in HEX_LOWER_A..HEX_LOWER_F || it in HEX_UPPER_A..HEX_UPPER_F }

    private fun isNumericLiteral(value: String): Boolean {
        // Int/Long/Float/Double with optional sign and suffix; hex/bin rejected (not needed).
        return NUMERIC_REGEX.matches(value)
    }

    private fun isStringLiteral(value: String): Boolean {
        if (value.length < AC.DEFAULT_EXPR_MIN_STRING_LITERAL_LEN) return false
        // Reject templates and triple quotes (keep whitelist tight).
        if (value.startsWith(AC.STR_TRIPLE_QUOTE)) return false
        if (!value.startsWith(AC.STR_DOUBLE_QUOTE) || !value.endsWith(AC.STR_DOUBLE_QUOTE)) return false
        var index = 1
        val end = value.length - 1
        while (index < end) {
            when (val ch = value[index]) {
                CHAR_DOLLAR -> return false // string template
                CHAR_BACKSLASH -> {
                    if (index + 1 >= end) return false
                    when (value[index + 1]) {
                        CHAR_BACKSLASH, CHAR_DOUBLE_QUOTE, CHAR_N, CHAR_T, CHAR_R, CHAR_B,
                        CHAR_DOLLAR, CHAR_SINGLE_QUOTE,
                            -> index += 2

                        CHAR_U -> {
                            if (index + 5 >= end) return false
                            val hex = value.substring(index + 2, index + 6)
                            if (!isHexDigits(value = hex)) return false
                            index += 6
                        }

                        else -> return false
                    }
                }

                CHAR_DOUBLE_QUOTE -> return false // premature end
                else -> index++
            }
        }
        return true
    }

    private fun matchesIdentifier(source: String, index: Int, name: String): Boolean {
        if (index + name.length > source.length) return false
        if (!source.regionMatches(index, name, 0, name.length)) return false
        val beforeOk = index == 0 || !source[index - 1].isJavaIdentifierPart()
        val after = index + name.length
        val afterOk = after >= source.length || !source[after].isJavaIdentifierPart()
        return beforeOk && afterOk
    }

    private fun offsetOfLine(source: String, lineNumber: Int): Int? {
        if (lineNumber < 1) return null
        var line = 1
        var index = 0
        while (index < source.length && line < lineNumber) {
            if (source[index] == CHAR_NEWLINE) line++
            index++
        }
        return if (line == lineNumber) index else null
    }

    private fun readExpression(source: String, from: Int): String? {
        var index = skipTrivia(source = source, from = from)
        if (index >= source.length) return null
        val start = index
        var angle = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (index < source.length) {
            when (val ch = source[index]) {
                CHAR_DOUBLE_QUOTE, CHAR_SINGLE_QUOTE -> index = skipLiteral(source = source, from = index)
                CHAR_SLASH -> {
                    // End expression before a line comment that isn't inside nesting.
                    val isTopLevelCommentStart = angle == 0 &&
                        paren == 0 &&
                        bracket == 0 &&
                        brace == 0 &&
                        index + 1 < source.length &&
                        source[index + 1] == CHAR_SLASH
                    if (isTopLevelCommentStart) {
                        break
                    }
                    val isCommentStart = index + 1 < source.length &&
                        (source[index + 1] == CHAR_SLASH || source[index + 1] == CHAR_STAR)
                    if (isCommentStart) {
                        index = skipTrivia(source = source, from = index)
                    } else {
                        index++
                    }
                }

                CHAR_ANGLE_OPEN -> {
                    angle++; index++
                }

                CHAR_ANGLE_CLOSE -> {
                    angle = (angle - 1).coerceAtLeast(0); index++
                }

                CHAR_PAREN_OPEN -> {
                    paren++; index++
                }

                CHAR_PAREN_CLOSE -> {
                    val isAtTopLevel = paren == 0 && angle == 0 && bracket == 0 && brace == 0
                    if (isAtTopLevel) break
                    paren = (paren - 1).coerceAtLeast(0)
                    index++
                }

                CHAR_BRACKET_OPEN -> {
                    bracket++; index++
                }

                CHAR_BRACKET_CLOSE -> {
                    bracket = (bracket - 1).coerceAtLeast(0); index++
                }

                CHAR_BRACE_OPEN -> {
                    brace++; index++
                }

                CHAR_BRACE_CLOSE -> {
                    brace = (brace - 1).coerceAtLeast(0); index++
                }

                CHAR_COMMA -> {
                    val isAtTopLevel = angle == 0 && paren == 0 && bracket == 0 && brace == 0
                    if (isAtTopLevel) break
                    index++
                }

                CHAR_NEWLINE -> {
                    // Newlines don't end the expression; only unnested `,`/`)` do.
                    index++
                }

                else -> index++
            }
        }
        if (index <= start) return null
        return source.substring(start, index).trim().trimEnd(CHAR_COMMA)
    }

    private fun skipLiteral(source: String, from: Int): Int {
        if (from >= source.length) return from
        val quote = source[from]
        if (quote != CHAR_DOUBLE_QUOTE && quote != CHAR_SINGLE_QUOTE) return from + 1
        val isTripleQuoteStart = quote == CHAR_DOUBLE_QUOTE &&
            from + 2 < source.length &&
            source[from + 1] == CHAR_DOUBLE_QUOTE &&
            source[from + 2] == CHAR_DOUBLE_QUOTE
        if (isTripleQuoteStart) {
            var index = from + 3
            while (index + 2 < source.length) {
                val isTripleQuoteAt = source[index] == CHAR_DOUBLE_QUOTE &&
                    source[index + 1] == CHAR_DOUBLE_QUOTE &&
                    source[index + 2] == CHAR_DOUBLE_QUOTE
                if (isTripleQuoteAt) {
                    return index + 3
                }
                index++
            }
            return source.length
        }
        var index = from + 1
        while (index < source.length) {
            when (source[index]) {
                CHAR_BACKSLASH -> index += 2
                quote -> return index + 1
                else -> index++
            }
        }
        return source.length
    }

    private fun skipTrivia(source: String, from: Int): Int {
        var index = from
        while (index < source.length) {
            when {
                source[index].isWhitespace() -> index++
                index + 1 < source.length &&
                        source[index] == CHAR_SLASH && source[index + 1] == CHAR_SLASH -> {
                    index += 2
                    while (index < source.length && source[index] != CHAR_NEWLINE) index++
                }

                index + 1 < source.length &&
                        source[index] == CHAR_SLASH && source[index + 1] == CHAR_STAR -> {
                    index += 2
                    while (index + 1 < source.length &&
                        !(source[index] == CHAR_STAR && source[index + 1] == CHAR_SLASH)
                    ) {
                        index++
                    }
                    index = (index + 2).coerceAtMost(source.length)
                }

                else -> return index
            }
        }
        return index
    }

    private val NUMERIC_REGEX = Regex(AC.REGEX_DEFAULT_EXPR_NUMERIC)
    private val ENUM_REF_REGEX = Regex(AC.REGEX_DEFAULT_EXPR_ENUM_REF)

    private const val CHAR_NEWLINE = '\n'
    private const val CHAR_DOUBLE_QUOTE = '"'
    private const val CHAR_SINGLE_QUOTE = '\''
    private const val CHAR_SLASH = '/'
    private const val CHAR_STAR = '*'
    private const val CHAR_COLON = ':'
    private const val CHAR_EQUALS = '='
    private const val CHAR_BANG = '!'
    private const val CHAR_COMMA = ','
    private const val CHAR_ANGLE_OPEN = '<'
    private const val CHAR_ANGLE_CLOSE = '>'
    private const val CHAR_PAREN_OPEN = '('
    private const val CHAR_PAREN_CLOSE = ')'
    private const val CHAR_BRACKET_OPEN = '['
    private const val CHAR_BRACKET_CLOSE = ']'
    private const val CHAR_BRACE_OPEN = '{'
    private const val CHAR_BRACE_CLOSE = '}'
    private const val CHAR_BACKSLASH = '\\'
    private const val CHAR_DOLLAR = '$'
    private const val CHAR_DOT = '.'
    private const val CHAR_UNDERSCORE = '_'
    private const val CHAR_N = 'n'
    private const val CHAR_T = 't'
    private const val CHAR_R = 'r'
    private const val CHAR_B = 'b'
    private const val CHAR_U = 'u'
    private const val HEX_LOWER_A = 'a'
    private const val HEX_LOWER_F = 'f'
    private const val HEX_UPPER_A = 'A'
    private const val HEX_UPPER_F = 'F'
}
