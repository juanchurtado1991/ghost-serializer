@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Whitespace, comment, indentation, and document-marker handling shared by the core block/flow
 * parser and the anchor/tag/flow-style subsystems. Called extremely frequently (every line
 * transition goes through [GhostYamlFlatReader.skipWhitespaceAndComments]).
 */

/** Skips spaces and tabs (inline whitespace — NOT newlines). */
internal fun GhostYamlFlatReader.skipInlineWhitespace() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit) {
        val currentByte = localRawData[position]
        if (currentByte != TOK.SPACE_BYTE && currentByte != TOK.TAB_BYTE) break
        position++
    }
}

/**
 * Skips all whitespace (including newlines) and full-line comments.
 * Updates [GhostYamlFlatReader.currentIndent] to the column of the next non-whitespace byte.
 */
internal fun GhostYamlFlatReader.skipWhitespaceAndComments() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit) {
        skipInlineWhitespace()
        if (position >= localLimit) break
        val currentByte = localRawData[position]
        when (currentByte) {
            TOK.NEWLINE_BYTE -> {
                position++
                currentIndent = 0
            }
            TOK.CR_BYTE -> {
                position++
                if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) position++
                currentIndent = 0
            }
            TOK.HASH_BYTE -> {
                // A comment must be preceded by whitespace or be first on its line —
                // "c,#invalid" isn't a comment, it's invalid trailing text touching real content.
                if (position > 0) {
                    val previousByte = localRawData[position - 1]
                    val isPrecededByWhitespace = isInlineWhitespaceByte(byte = previousByte) || isLineBreakByte(byte = previousByte)
                    if (!isPrecededByWhitespace) {
                        yamlError(message = EM.ERR_COMMENT_NEEDS_WHITESPACE)
                    }
                }
                skipToEndOfLine()
            }
            else -> {
                break
            }
        }
    }
    recomputeCurrentIndent()
}

/**
 * Recomputes [GhostYamlFlatReader.currentIndent] by counting leading spaces on the current line,
 * and [GhostYamlFlatReader.indentHasTab] by checking whether a tab immediately follows those
 * spaces (i.e. is part of the line's leading whitespace, before any real content).
 */
private fun GhostYamlFlatReader.recomputeCurrentIndent() {
    val localLimit = limit
    val localRawData = rawData
    var lineStart = position
    while (lineStart > 0 && !isLineBreakByte(byte = localRawData[lineStart - 1])) {
        lineStart--
    }
    var spaces = 0
    var pointer = lineStart
    while (pointer < localLimit && localRawData[pointer] == TOK.SPACE_BYTE) {
        spaces++; pointer++
    }
    currentIndent = spaces
    indentHasTab = pointer < localLimit && localRawData[pointer] == TOK.TAB_BYTE
}

/** Advances [GhostYamlFlatReader.position] to the next newline (exclusive). */
internal fun GhostYamlFlatReader.skipToEndOfLine() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit && !isLineBreakByte(byte = localRawData[position])) {
        position++
    }
}

internal fun GhostYamlFlatReader.advanceLine() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit && !isLineBreakByte(byte = localRawData[position])) {
        position++
    }
    if (position < localLimit && localRawData[position] == TOK.CR_BYTE) position++
    if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) position++
    currentIndent = 0
}

/**
 * Skips `%YAML`/`%TAG` directives and an optional `---` document-start marker. Returns true if
 * an explicit `---` was consumed, so callers can tell an explicit-but-empty document (`---`
 * immediately followed by EOF, valid with a null value) apart from having no more input.
 */
internal fun GhostYamlFlatReader.skipDirectivesAndDocumentStart(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    var sawDirective = false
    var sawYamlDirective = false
    while (position < localLimit) {
        skipInlineWhitespace()
        if (position >= localLimit) break
        when (localRawData[position]) {
            TOK.PERCENT_BYTE -> {
                sawDirective = true
                position++ // consume '%'
                val dirStart = position
                while (position < localLimit && !isDirectiveTokenTerminator(byte = localRawData[position])) {
                    position++
                }
                val dirName = localRawData.decodeToString(dirStart, position)
                skipInlineWhitespace()
                when (dirName) {
                    TOK.STR_TAG_DIRECTIVE -> {
                        val handleStart = position
                        while (position < localLimit && !isDirectiveTokenTerminator(byte = localRawData[position])) {
                            position++
                        }
                        val handle = localRawData.decodeToString(handleStart, position)
                        skipInlineWhitespace()
                        val prefixStart = position
                        while (position < localLimit && !isDirectiveValueTerminator(byte = localRawData[position])) {
                            position++
                        }
                        val prefix = localRawData.decodeToString(prefixStart, position)
                        tagDirectives[handle] = prefix
                    }

                    TOK.STR_YAML_DIRECTIVE -> {
                        if (sawYamlDirective) yamlError(message = EM.ERR_DUPLICATE_YAML_DIRECTIVE)
                        sawYamlDirective = true
                        val versionStart = position
                        while (position < localLimit && !isDirectiveValueTerminator(byte = localRawData[position])) {
                            position++
                        }
                        val version = localRawData.decodeToString(versionStart, position)
                        if (!isYamlVersionToken(version = version)) {
                            yamlError(message = "${EM.ERR_MALFORMED_YAML_VERSION_PREFIX}$version")
                        }
                        skipInlineWhitespace()
                        if (position < localLimit) {
                            val trailingByte = localRawData[position]
                            val isInvalidTrailingByte = trailingByte != TOK.NEWLINE_BYTE &&
                                trailingByte != TOK.CR_BYTE && trailingByte != TOK.HASH_BYTE
                            if (isInvalidTrailingByte) {
                                yamlError(message = EM.ERR_UNEXPECTED_AFTER_YAML_DIRECTIVE)
                            }
                        }
                    }
                }
                skipToEndOfLine()
            }

            TOK.DASH_BYTE -> if (isDocumentMarker()) {
                position += TOK.DOC_MARKER_LEN
                return true
            } else break

            TOK.NEWLINE_BYTE -> {
                position++; currentIndent = 0
            }

            TOK.CR_BYTE -> {
                position++
                if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) position++
                currentIndent = 0
            }

            TOK.HASH_BYTE -> skipToEndOfLine()
            else -> break
        }
    }
    if (sawDirective) yamlError(message = EM.ERR_DIRECTIVES_NEED_DOC_START)
    return false
}

/** True if [byte] ends a directive name/handle token (inline whitespace only). */
private fun isDirectiveTokenTerminator(byte: Byte): Boolean =
    byte == TOK.SPACE_BYTE || byte == TOK.TAB_BYTE

/** True if [byte] ends a directive value token (inline whitespace or a line break). */
private fun isDirectiveValueTerminator(byte: Byte): Boolean =
    isDirectiveTokenTerminator(byte = byte) || byte == TOK.NEWLINE_BYTE || byte == TOK.CR_BYTE

/** True if [version] is a bare `major.minor` YAML version token, e.g. `"1.2"`. */
private fun isYamlVersionToken(version: String): Boolean {
    val dot = version.indexOf('.')
    if (dot <= 0 || dot == version.length - 1) return false
    for (i in version.indices) {
        if (i != dot && version[i] !in '0'..'9') return false
    }
    return true
}

/**
 * Skips a `...` document end marker if present, requiring only whitespace or a comment to
 * follow on the same line. Returns true if a marker was consumed — after an explicit `...`, the
 * *next* document may start without a `---` at all, so callers should skip trailing-content
 * restrictions when this returns true.
 */
internal fun GhostYamlFlatReader.skipDocumentEnd(): Boolean {
    skipWhitespaceAndComments()
    val localLimit = limit
    val localRawData = rawData
    val isThreeDots = position + TOK.DOC_MARKER_LEN <= localLimit &&
        localRawData[position] == TOK.DOT_BYTE &&
        localRawData[position + 1] == TOK.DOT_BYTE &&
        localRawData[position + 2] == TOK.DOT_BYTE
    if (isThreeDots) {
        position += TOK.DOC_MARKER_LEN
        skipInlineWhitespace()
        if (position < localLimit) {
            val trailingByte = localRawData[position]
            if (trailingByte == TOK.HASH_BYTE) {
                skipToEndOfLine()
            } else if (!isLineBreakByte(byte = trailingByte)) {
                yamlError(message = EM.ERR_UNEXPECTED_AFTER_DOC_END)
            }
        }
        return true
    }
    return false
}

internal fun GhostYamlFlatReader.isDocumentMarker(): Boolean = isThreeByteMarker(markerByte = TOK.DASH_BYTE)

internal fun GhostYamlFlatReader.isDocumentEndMarker(): Boolean = isThreeByteMarker(markerByte = TOK.DOT_BYTE)

/** True if the 3 bytes at the current position all equal [markerByte], followed by whitespace/EOF. */
private inline fun GhostYamlFlatReader.isThreeByteMarker(markerByte: Byte): Boolean {
    val localLimit = limit
    val localRawData = rawData
    if (position + TOK.DOC_MARKER_LEN > localLimit) return false
    return localRawData[position] == markerByte &&
        localRawData[position + 1] == markerByte &&
        localRawData[position + 2] == markerByte &&
        (position + TOK.DOC_MARKER_LEN >= localLimit ||
            isMarkerTerminator(byte = localRawData[position + TOK.DOC_MARKER_LEN]))
}

/** True if the current position starts a block sequence entry (`- `). */
internal fun GhostYamlFlatReader.isBlockSequenceEntry(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    if (position >= localLimit || localRawData[position] != TOK.DASH_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= localLimit || isMarkerTerminator(byte = localRawData[nextPosition])
}

/** True if [byte] can follow a structural marker (`---`, `...`, `- `): whitespace or a line break. */
private fun isMarkerTerminator(byte: Byte): Boolean =
    byte == TOK.SPACE_BYTE || byte == TOK.NEWLINE_BYTE || byte == TOK.CR_BYTE || byte == TOK.TAB_BYTE

/** True if [byte] is a line-break byte (`\n` or `\r`), shared across every reading subsystem. */
internal inline fun isLineBreakByte(byte: Byte): Boolean = byte == TOK.NEWLINE_BYTE || byte == TOK.CR_BYTE

/** True if [byte] is inline whitespace (space or tab), shared across every reading subsystem. */
internal inline fun isInlineWhitespaceByte(byte: Byte): Boolean = byte == TOK.SPACE_BYTE || byte == TOK.TAB_BYTE

/** Trims trailing spaces/tabs between [start] and [end], returning the new end. */
internal fun GhostYamlFlatReader.trimTrailingSpaces(start: Int, end: Int): Int {
    val localRawData = rawData
    var endPos = end
    while (endPos > start && isInlineWhitespaceByte(byte = localRawData[endPos - 1])) endPos--
    return endPos
}
