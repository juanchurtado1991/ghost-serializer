package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File

/**
 * Extracts constructor-parameter default expressions from Kotlin source text.
 *
 * KSP only exposes [KSValueParameter.hasDefault], never the expression itself. When the
 * declaring file is available via [FileLocation], this extractor reads the source, isolates
 * the parameter's RHS, and accepts it only when it matches a strict literal whitelist.
 * Anything unrecognized returns `null` so callers can fall back to `.copy()`.
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
        val raw = extractRawDefault(source, name, location.lineNumber) ?: return null
        return whitelist(raw)
    }

    /**
     * Isolates the raw default RHS for [paramName] near [lineNumber] (1-based) without
     * whitelist filtering. Visible for unit tests.
     */
    internal fun extractRawDefault(source: String, paramName: String, lineNumber: Int): String? {
        val startOffset = offsetOfLine(source, lineNumber) ?: return null
        val paramStart = findParameterName(source, paramName, startOffset) ?: return null
        val eqIndex = findDefaultEquals(source, paramStart + paramName.length) ?: return null
        return readExpression(source, eqIndex + 1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns [expr] unchanged when it is a whitelisted literal; otherwise `null`.
     */
    internal fun whitelist(expr: String): String? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed == "null" || trimmed == "true" || trimmed == "false" -> trimmed
            isNumericLiteral(trimmed) -> trimmed
            isCharLiteral(trimmed) -> trimmed
            isStringLiteral(trimmed) -> trimmed
            isEmptyCollectionCall(trimmed) -> trimmed
            isEnumOrConstRef(trimmed) -> trimmed
            else -> null
        }
    }

    private fun offsetOfLine(source: String, lineNumber: Int): Int? {
        if (lineNumber < 1) return null
        var line = 1
        var i = 0
        while (i < source.length && line < lineNumber) {
            if (source[i] == '\n') line++
            i++
        }
        return if (line == lineNumber) i else null
    }

    /**
     * Finds [paramName] as a constructor parameter identifier at or after [from],
     * skipping string/char literals and comments.
     */
    private fun findParameterName(source: String, paramName: String, from: Int): Int? {
        var i = from.coerceAtLeast(0)
        // Search a bounded window: parameter declarations rarely span far past location.
        val end = (from + MAX_PARAM_SEARCH_CHARS).coerceAtMost(source.length)
        while (i < end) {
            i = skipTrivia(source, i)
            if (i >= end) break
            when (val c = source[i]) {
                '"', '\'' -> i = skipLiteral(source, i)
                '/' -> {
                    val next = skipTrivia(source, i)
                    if (next == i) i++ else i = next
                }
                else -> {
                    if (c.isJavaIdentifierStart() && matchesIdentifier(source, i, paramName)) {
                        val after = i + paramName.length
                        // Must look like a parameter binder: name followed by ':' or annotation-free type start.
                        // Reject when the identifier is a receiver/qualifier (name.) or call (name().
                        if (after < source.length) {
                            val nextSignificant = skipTrivia(source, after)
                            if (nextSignificant < source.length) {
                                val n = source[nextSignificant]
                                // Parameter form: `name:` or `name /* */ :` — also allow `@Ann name:`
                                // after we already landed on the name.
                                if (n == ':') return i
                            }
                        }
                    }
                    // Advance one identifier or one char.
                    if (c.isJavaIdentifierStart()) {
                        i++
                        while (i < end && source[i].isJavaIdentifierPart()) i++
                    } else {
                        i++
                    }
                }
            }
        }
        // Fallback: scan from file start near the same line window if the location pointed
        // at an annotation above the parameter.
        if (from > 0) {
            val back = (from - MAX_PARAM_BACKTRACK_CHARS).coerceAtLeast(0)
            return findParameterName(source, paramName, back)?.takeIf { it >= back }
                ?: findParameterNameFromConstructor(source, paramName)
        }
        return findParameterNameFromConstructor(source, paramName)
    }

    private fun findParameterNameFromConstructor(source: String, paramName: String): Int? {
        // Last-resort scan: any `paramName:` that precedes an `=` before the next top-level comma/')'.
        var i = 0
        while (i < source.length) {
            i = skipTrivia(source, i)
            if (i >= source.length) break
            if (source[i].isJavaIdentifierStart() && matchesIdentifier(source, i, paramName)) {
                val afterName = skipTrivia(source, i + paramName.length)
                if (afterName < source.length && source[afterName] == ':') {
                    val eq = findDefaultEquals(source, afterName + 1)
                    if (eq != null) return i
                }
                i += paramName.length
            } else if (source[i] == '"' || source[i] == '\'') {
                i = skipLiteral(source, i)
            } else {
                i++
            }
        }
        return null
    }

    private fun matchesIdentifier(source: String, index: Int, name: String): Boolean {
        if (index + name.length > source.length) return false
        if (!source.regionMatches(index, name, 0, name.length)) return false
        val beforeOk = index == 0 || !source[index - 1].isJavaIdentifierPart()
        val after = index + name.length
        val afterOk = after >= source.length || !source[after].isJavaIdentifierPart()
        return beforeOk && afterOk
    }

    /**
     * After the parameter name, skip the type (and annotations) until the default `=` at depth 0.
     */
    private fun findDefaultEquals(source: String, from: Int): Int? {
        var i = from
        var angle = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < source.length) {
            i = skipTrivia(source, i)
            if (i >= source.length) return null
            when (val c = source[i]) {
                '"' , '\'' -> i = skipLiteral(source, i)
                '<' -> { angle++; i++ }
                '>' -> { angle = (angle - 1).coerceAtLeast(0); i++ }
                '(' -> { paren++; i++ }
                ')' -> {
                    if (paren == 0 && angle == 0 && bracket == 0 && brace == 0) return null
                    paren = (paren - 1).coerceAtLeast(0)
                    i++
                }
                '[' -> { bracket++; i++ }
                ']' -> { bracket = (bracket - 1).coerceAtLeast(0); i++ }
                '{' -> { brace++; i++ }
                '}' -> { brace = (brace - 1).coerceAtLeast(0); i++ }
                '=' -> {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) {
                        // Reject `==` / `!=` / `*=` etc. — default uses a single `=`.
                        val prev = source.getOrNull(i - 1)
                        val next = source.getOrNull(i + 1)
                        if (next != '=' && prev != '!' && prev != '<' && prev != '>' &&
                            prev != ':' // not relevant but keep `=` only
                        ) {
                            return i
                        }
                    }
                    i++
                }
                ',' -> {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) return null
                    i++
                }
                else -> i++
            }
        }
        return null
    }

    private fun readExpression(source: String, from: Int): String? {
        var i = skipTrivia(source, from)
        if (i >= source.length) return null
        val start = i
        var angle = 0
        var paren = 0
        var bracket = 0
        var brace = 0
        while (i < source.length) {
            when (val c = source[i]) {
                '"' , '\'' -> i = skipLiteral(source, i)
                '/' -> {
                    // End expression before a line comment that isn't inside nesting.
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0 &&
                        i + 1 < source.length && source[i + 1] == '/'
                    ) {
                        break
                    }
                    if (i + 1 < source.length && (source[i + 1] == '/' || source[i + 1] == '*')) {
                        i = skipTrivia(source, i)
                    } else {
                        i++
                    }
                }
                '<' -> { angle++; i++ }
                '>' -> { angle = (angle - 1).coerceAtLeast(0); i++ }
                '(' -> { paren++; i++ }
                ')' -> {
                    if (paren == 0 && angle == 0 && bracket == 0 && brace == 0) break
                    paren = (paren - 1).coerceAtLeast(0)
                    i++
                }
                '[' -> { bracket++; i++ }
                ']' -> { bracket = (bracket - 1).coerceAtLeast(0); i++ }
                '{' -> { brace++; i++ }
                '}' -> { brace = (brace - 1).coerceAtLeast(0); i++ }
                ',' -> {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) break
                    i++
                }
                '\n' -> {
                    // Allow multiline defaults while nested; at depth 0 keep going until `,` / `)`.
                    i++
                }
                else -> i++
            }
        }
        if (i <= start) return null
        return source.substring(start, i).trim().trimEnd(',')
    }

    private fun skipTrivia(source: String, from: Int): Int {
        var i = from
        while (i < source.length) {
            when {
                source[i].isWhitespace() -> i++
                i + 1 < source.length && source[i] == '/' && source[i + 1] == '/' -> {
                    i += 2
                    while (i < source.length && source[i] != '\n') i++
                }
                i + 1 < source.length && source[i] == '/' && source[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(source.length)
                }
                else -> return i
            }
        }
        return i
    }

    private fun skipLiteral(source: String, from: Int): Int {
        if (from >= source.length) return from
        val quote = source[from]
        if (quote != '"' && quote != '\'') return from + 1
        // Triple-quoted string.
        if (quote == '"' && from + 2 < source.length &&
            source[from + 1] == '"' && source[from + 2] == '"'
        ) {
            var i = from + 3
            while (i + 2 < source.length) {
                if (source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                    return i + 3
                }
                i++
            }
            return source.length
        }
        var i = from + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    private fun isNumericLiteral(s: String): Boolean {
        // Int/Long/Float/Double with optional sign and suffix; hex/bin rejected (not needed).
        return NUMERIC_REGEX.matches(s)
    }

    private fun isCharLiteral(s: String): Boolean {
        if (s.length < 3 || s.first() != '\'' || s.last() != '\'') return false
        val inner = s.substring(1, s.length - 1)
        return when {
            inner.length == 1 && inner[0] != '\\' && inner[0] != '\'' -> true
            inner == "\\'" || inner == "\\\\" || inner == "\\n" || inner == "\\t" ||
                inner == "\\r" || inner == "\\b" || inner == "\\\$" -> true
            inner.startsWith("\\u") && inner.length == 6 &&
                inner.substring(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> true
            else -> false
        }
    }

    private fun isStringLiteral(s: String): Boolean {
        if (s.length < 2) return false
        // Reject templates and triple quotes (keep whitelist tight).
        if (s.startsWith("\"\"\"")) return false
        if (!s.startsWith("\"") || !s.endsWith("\"")) return false
        var i = 1
        val end = s.length - 1
        while (i < end) {
            when (val c = s[i]) {
                '$' -> return false // string template
                '\\' -> {
                    if (i + 1 >= end) return false
                    when (s[i + 1]) {
                        '\\', '"', 'n', 't', 'r', 'b', '$', '\'' -> i += 2
                        'u' -> {
                            if (i + 5 >= end) return false
                            val hex = s.substring(i + 2, i + 6)
                            if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return false
                            i += 6
                        }
                        else -> return false
                    }
                }
                '"' -> return false // premature end
                else -> i++
            }
        }
        return true
    }

    private fun isEmptyCollectionCall(s: String): Boolean {
        return s in EMPTY_COLLECTION_CALLS
    }

    private fun isEnumOrConstRef(s: String): Boolean {
        // Qual.Name or Name — identifiers joined by dots, no calls/generics.
        if (!ENUM_REF_REGEX.matches(s)) return false
        // Reject lowercase-only single identifiers that look like variables (a, foo).
        // Allow ALL_CAPS consts and Capitalized enum entries / class refs.
        val parts = s.split('.')
        return parts.all { part ->
            part.first().isUpperCase() || part.all { it == '_' || it.isDigit() || it.isUpperCase() }
        }
    }

    private const val MAX_PARAM_SEARCH_CHARS = 8_192
    private const val MAX_PARAM_BACKTRACK_CHARS = 512

    private val EMPTY_COLLECTION_CALLS = setOf(
        "emptyList()",
        "emptySet()",
        "emptyMap()",
        "listOf()",
        "setOf()",
        "mapOf()",
    )

    private val NUMERIC_REGEX = Regex(
        """^-?(?:""" +
            """(?:0|[1-9]\d*)(?:L|l)?|""" +
            """(?:0|[1-9]\d*)\.\d+(?:[eE][+-]?\d+)?[fFdD]?|""" +
            """(?:0|[1-9]\d*)(?:[eE][+-]?\d+)[fFdD]?|""" +
            """(?:0|[1-9]\d*)[fFdD]""" +
            """)$"""
    )

    private val ENUM_REF_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$""")
}
