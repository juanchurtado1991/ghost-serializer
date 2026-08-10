package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Whitespace, comment, indentation, and document-marker handling shared by the core block/flow
 * parser and the anchor/tag/flow-style subsystems. Called extremely frequently (every line
 * transition goes through [GhostYamlFlatReader.skipWhitespaceAndComments]), but moving it to its
 * own file has no runtime cost — see the split's commit message for why.
 */

/** Skips spaces and tabs (inline whitespace — NOT newlines). */
internal fun GhostYamlFlatReader.skipInlineWhitespace() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit) {
        val currentByte = localRawData[position]
        if (currentByte != C.SPACE_BYTE && currentByte != C.TAB_BYTE) break
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
        when {
            currentByte == C.NEWLINE_BYTE -> {
                position++
                currentIndent = 0
            }

            currentByte == C.CR_BYTE -> {
                position++
                if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
                currentIndent = 0
            }

            currentByte == C.HASH_BYTE -> {
                // A comment must be preceded by whitespace or be the first thing on its line —
                // "c,#invalid" and "\"value\"#comment" aren't comments, they're invalid trailing
                // text directly touching real content.
                if (position > 0) {
                    val previousByte = localRawData[position - 1]
                    if (previousByte != C.SPACE_BYTE && previousByte != C.TAB_BYTE &&
                        previousByte != C.NEWLINE_BYTE && previousByte != C.CR_BYTE
                    ) {
                        yamlError(C.ERR_COMMENT_NEEDS_WHITESPACE)
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
    while (lineStart > 0 && localRawData[lineStart - 1] != C.NEWLINE_BYTE && localRawData[lineStart - 1] != C.CR_BYTE) {
        lineStart--
    }
    var spaces = 0
    var pointer = lineStart
    while (pointer < localLimit && localRawData[pointer] == C.SPACE_BYTE) {
        spaces++; pointer++
    }
    currentIndent = spaces
    indentHasTab = pointer < localLimit && localRawData[pointer] == C.TAB_BYTE
}

/** Advances [GhostYamlFlatReader.position] to the next newline (exclusive). */
internal fun GhostYamlFlatReader.skipToEndOfLine() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
        position++
    }
}

/** Advances past the current newline character(s). */
internal fun GhostYamlFlatReader.advanceLine() {
    val localLimit = limit
    val localRawData = rawData
    while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
        position++
    }
    if (position < localLimit && localRawData[position] == C.CR_BYTE) position++
    if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
    currentIndent = 0
}

/**
 * Skips `%YAML`/`%TAG` directives and an optional `---` document-start marker.
 *
 * Returns true if an explicit `---` marker was consumed. Callers need this to tell an
 * explicit-but-empty document (`---` followed immediately by end of input, which is a valid
 * document whose value is null) apart from genuinely having no more input to read.
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
            C.PERCENT_BYTE -> {
                sawDirective = true
                position++ // consume '%'
                val dirStart = position
                while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE) {
                    position++
                }
                val dirName = localRawData.decodeToString(dirStart, position)
                skipInlineWhitespace()
                when (dirName) {
                    C.STR_TAG_DIRECTIVE -> {
                        val handleStart = position
                        while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE) {
                            position++
                        }
                        val handle = localRawData.decodeToString(handleStart, position)
                        skipInlineWhitespace()
                        val prefixStart = position
                        while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE &&
                            localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE
                        ) {
                            position++
                        }
                        val prefix = localRawData.decodeToString(prefixStart, position)
                        tagDirectives[handle] = prefix
                    }

                    C.STR_YAML_DIRECTIVE -> {
                        if (sawYamlDirective) yamlError(C.ERR_DUPLICATE_YAML_DIRECTIVE)
                        sawYamlDirective = true
                        val versionStart = position
                        while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE &&
                            localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE
                        ) {
                            position++
                        }
                        val version = localRawData.decodeToString(versionStart, position)
                        if (!isYamlVersionToken(version)) {
                            yamlError("${C.ERR_MALFORMED_YAML_VERSION_PREFIX}$version")
                        }
                        skipInlineWhitespace()
                        if (position < localLimit) {
                            val trailingByte = localRawData[position]
                            if (trailingByte != C.NEWLINE_BYTE && trailingByte != C.CR_BYTE && trailingByte != C.HASH_BYTE) {
                                yamlError(C.ERR_UNEXPECTED_AFTER_YAML_DIRECTIVE)
                            }
                        }
                    }
                }
                skipToEndOfLine()
            }

            C.DASH_BYTE -> if (isDocumentMarker()) {
                position += C.DOC_MARKER_LEN
                return true
            } else break

            C.NEWLINE_BYTE -> {
                position++; currentIndent = 0
            }

            C.CR_BYTE -> {
                position++
                if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
                currentIndent = 0
            }

            C.HASH_BYTE -> skipToEndOfLine()
            else -> break
        }
    }
    if (sawDirective) yamlError(C.ERR_DIRECTIVES_NEED_DOC_START)
    return false
}

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
 * follow on the same line. Returns true if a marker was consumed — after an explicit `...`,
 * the *next* document is allowed to start without a `---` at all (per the YAML stream grammar),
 * so callers should not apply trailing-content-after-a-document restrictions when this returns
 * true.
 */
internal fun GhostYamlFlatReader.skipDocumentEnd(): Boolean {
    skipWhitespaceAndComments()
    val localLimit = limit
    val localRawData = rawData
    if (position + 2 < localLimit &&
        localRawData[position] == C.DOT_BYTE &&
        localRawData[position + 1] == C.DOT_BYTE &&
        localRawData[position + 2] == C.DOT_BYTE
    ) {
        position += C.DOC_MARKER_LEN
        skipInlineWhitespace()
        if (position < localLimit) {
            val trailingByte = localRawData[position]
            if (trailingByte == C.HASH_BYTE) {
                skipToEndOfLine()
            } else if (trailingByte != C.NEWLINE_BYTE && trailingByte != C.CR_BYTE) {
                yamlError(C.ERR_UNEXPECTED_AFTER_DOC_END)
            }
        }
        return true
    }
    return false
}

/** Returns true if current position is at a `---` marker at column 0. */
internal fun GhostYamlFlatReader.isDocumentMarker(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    if (position + 2 >= localLimit) return false
    return localRawData[position] == C.DASH_BYTE &&
            localRawData[position + 1] == C.DASH_BYTE &&
            localRawData[position + 2] == C.DASH_BYTE &&
            (position + 3 >= localLimit ||
                    localRawData[position + 3] == C.SPACE_BYTE ||
                    localRawData[position + 3] == C.NEWLINE_BYTE ||
                    localRawData[position + 3] == C.CR_BYTE ||
                    localRawData[position + 3] == C.TAB_BYTE)
}

/** Returns true if current position is at a `...` marker at column 0. */
internal fun GhostYamlFlatReader.isDocumentEndMarker(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    if (position + 2 >= localLimit) return false
    return localRawData[position] == C.DOT_BYTE &&
            localRawData[position + 1] == C.DOT_BYTE &&
            localRawData[position + 2] == C.DOT_BYTE &&
            (position + 3 >= localLimit ||
                    localRawData[position + 3] == C.SPACE_BYTE ||
                    localRawData[position + 3] == C.NEWLINE_BYTE ||
                    localRawData[position + 3] == C.CR_BYTE ||
                    localRawData[position + 3] == C.TAB_BYTE)
}

/** Returns true if current position is at the start of a block sequence entry `- `. */
internal fun GhostYamlFlatReader.isBlockSequenceEntry(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    if (position >= localLimit || localRawData[position] != C.DASH_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= localLimit ||
            localRawData[nextPosition] == C.SPACE_BYTE ||
            localRawData[nextPosition] == C.NEWLINE_BYTE ||
            localRawData[nextPosition] == C.CR_BYTE ||
            localRawData[nextPosition] == C.TAB_BYTE
}

/** Trims trailing spaces between [start] and [end], returning the new end. */
internal fun GhostYamlFlatReader.trimTrailingSpaces(start: Int, end: Int): Int {
    val localRawData = rawData
    var endPos = end
    while (endPos > start &&
        (localRawData[endPos - 1] == C.SPACE_BYTE || localRawData[endPos - 1] == C.TAB_BYTE)
    ) endPos--
    return endPos
}
