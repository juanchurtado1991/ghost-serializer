package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Reads either a plain scalar (string, int, float, bool, null) or detects
 * that the current content is actually a block mapping key.
 */
internal fun GhostYamlFlatReader.readPlainScalarOrMapping(
    indent: Int,
    inFlow: Boolean,
    expectedTag: Int = GhostYamlTags.TAG_NONE,
    allowMappingRedirect: Boolean = true,
    foldIndent: Int = indent
): Any? {
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Scan forward to find ':' or end-of-line
    var scanPosition = position
    while (scanPosition < localLimit) {
        val currentByte = localRawData[scanPosition]
        when {
            currentByte == TOK.COLON_BYTE -> {
                // ':' followed by space/newline/EOF → this is a mapping key
                if (isMappingColonAt(rawData = localRawData, position = scanPosition, limit = localLimit)) {
                    if (!inFlow) {
                        if (!allowMappingRedirect) {
                            // This colon is an *inline* value/key-node on the same line as an
                            // enclosing key's own ':' (e.g. "b: c" in "a: b: c") — no fresh,
                            // more-indented line for a nested mapping, so it's ambiguous, not
                            // a legal redirect. yaml-test-suite case ZCZ6.
                            yamlError(message = EM.ERR_UNEXPECTED_INLINE_NESTED_MAPPING_COLON)
                        }
                        // Rewind and parse as block mapping
                        position = startPosition
                        return readBlockMapping(blockIndent = indent.coerceAtLeast(0))
                    }
                    // No block mapping to redirect into inside a flow collection — stop the
                    // scalar here and let the caller decide what the colon means.
                    break
                }
                scanPosition++
            }

            isLineBreakByte(byte = currentByte) -> break
            currentByte == TOK.HASH_BYTE -> {
                // Inline comment ends the scalar before '#', only if preceded by whitespace
                // ("d#X" isn't a comment, "d #X" is).
                if (scanPosition > startPosition && isInlineWhitespaceByte(byte = localRawData[scanPosition - 1])) break
                scanPosition++
            }

            inFlow && isFlowScalarTerminatorByte(byte = currentByte) -> break
            else -> scanPosition++
        }
    }

    val endPosition = trimTrailingSpaces(start = startPosition, end = scanPosition)
    position = scanPosition

    // A bare "-" is a valid plain-scalar start only when followed by a "safe" character
    // (ns-plain-first's rule for '-'/'?'/':') — in flow context that excludes flow
    // indicators (",[]{}"), so a lone "-" touching one (e.g. "[-, -]") is neither a block
    // sequence indicator (flow has none) nor a valid plain scalar.
    val isLoneFlowDash = inFlow && endPosition - startPosition == 1 && localRawData[startPosition] == TOK.DASH_BYTE
    if (isLoneFlowDash) {
        yamlError(message = EM.ERR_LONE_DASH_IN_FLOW)
    }

    // Plain scalars continue onto following lines: more-indented lines in block context
    // ([foldPlainScalarContinuation]), any non-terminator line in flow context
    // ([foldFlowPlainScalarContinuation]). Folding rule either way: a single newline becomes
    // a space; N blank lines become N newlines (same as readBlockScalarContent's ">" style).
    if (position < localLimit && isLineBreakByte(byte = localRawData[position])) {
        val firstLine = localRawData.decodeToString(startPosition, endPosition)
        val folded = if (inFlow) {
            foldFlowPlainScalarContinuation(firstLine = firstLine)
        } else {
            foldPlainScalarContinuation(indent = foldIndent, firstLine = firstLine)
        }
        if (folded != null) {
            val foldedBytes = folded.encodeToByteArray()
            return interpretScalar(data = foldedBytes, start = 0, end = foldedBytes.size, expectedTag = expectedTag)
        }
    }
    return interpretScalar(data = localRawData, start = startPosition, end = endPosition, expectedTag = expectedTag)
}

internal fun GhostYamlFlatReader.readPlainScalar(
    indent: Int,
    inFlow: Boolean,
    expectedTag: Int = GhostYamlTags.TAG_NONE,
    allowMappingRedirect: Boolean = true
): Any? =
    readPlainScalarOrMapping(
        indent = indent,
        inFlow = inFlow,
        expectedTag = expectedTag,
        allowMappingRedirect = allowMappingRedirect
    )

/**
 * Consumes following lines that continue a plain scalar (each more indented than [indent],
 * the enclosing block's indentation), folding them onto [firstLine] per YAML's line-folding
 * rule. Returns `null` (without moving [position]) if the next line isn't a continuation, so
 * the caller falls back to its single-line result.
 */
private fun GhostYamlFlatReader.foldPlainScalarContinuation(indent: Int, firstLine: String): String? {
    val localRawData = rawData
    val localLimit = limit
    val scalarEndPosition = position
    var folded: StringBuilder? = null
    var blankLines = 0

    while (true) {
        val beforeNewline = position
        // Blank-ness is judged after skipping *all* leading whitespace (spaces and tabs) — a
        // line that's purely whitespace is blank even if it includes a tab.
        val blankScan = scanFoldBlankLine(rawData = localRawData, position = position, limit = localLimit)
        if (blankScan.isBlank) {
            blankLines++
            position = blankScan.contentStart
            if (position >= localLimit) break
            continue
        }
        position = blankScan.afterNewline

        // How "indented" this line is (continuation-vs-dedent) is judged by real spaces only
        // — a tab can't establish block-structure indentation (see recomputeCurrentIndent/
        // indentHasTab), else a tab at a sibling key's indentation would falsely read as
        // "still a continuation" instead of ambiguous/invalid. Once continuation is
        // confirmed, tabs after those spaces are ordinary leading whitespace like the spaces.
        var spaces = 0
        var peekPos = position
        while (peekPos < localLimit && localRawData[peekPos] == TOK.SPACE_BYTE) {
            spaces++; peekPos++
        }
        while (peekPos < localLimit && localRawData[peekPos] == TOK.TAB_BYTE) {
            peekPos++
        }

        position = peekPos
        val endsPlainScalar = spaces <= indent || isDocumentMarker() || isDocumentEndMarker()
        if (endsPlainScalar) {
            // Not a continuation (dedented, sibling-level, or a new document). If we already
            // folded a real continuation line, rewind just past it (trailing blank lines
            // aren't part of the value); otherwise rewind to right after the first line,
            // undoing any tentatively-scanned blank lines so the caller sees them again.
            position = if (folded != null) beforeNewline else scalarEndPosition
            break
        }

        val lineStart = position
        var sawComment = false
        while (position < localLimit && !isLineBreakByte(byte = localRawData[position])) {
            val currentByte = localRawData[position]
            // A comment ends this line the same way it ends a single-line plain scalar (only
            // when preceded by whitespace) — and since a comment can't appear inside a
            // still-open fold, it ends the whole scalar here, not just this line (case BF9H).
            val isWhitespacePrecededComment = currentByte == TOK.HASH_BYTE && position > lineStart &&
                isInlineWhitespaceByte(byte = localRawData[position - 1])
            if (isWhitespacePrecededComment) {
                sawComment = true
                break
            }
            position++
        }
        val lineEnd = trimTrailingSpaces(start = lineStart, end = position)
        // A continuation line can't itself look like a mapping key ("word: ") — a plain
        // scalar's fold can't contain a nested key/value pair (case 2CMS).
        var colonScan = lineStart
        while (colonScan < lineEnd) {
            if (localRawData[colonScan] == TOK.COLON_BYTE) {
                val afterColon = colonScan + 1
                val isColonFollowedByBlank = afterColon >= lineEnd ||
                    localRawData[afterColon] == TOK.SPACE_BYTE ||
                    localRawData[afterColon] == TOK.TAB_BYTE
                if (isColonFollowedByBlank) {
                    yamlError(message = EM.ERR_PLAIN_CONTINUATION_MAPPING_KEY)
                }
            }
            colonScan++
        }
        val lineText = localRawData.decodeToString(lineStart, lineEnd)

        if (folded == null) folded = StringBuilder(firstLine)
        appendFoldedLine(builder = folded, blankLines = blankLines, lineText = lineText)
        blankLines = 0

        // Leave position at the '#' — the caller's skipWhitespaceAndComments handles it.
        if (sawComment) break
        if (position >= localLimit) break
    }

    return folded?.toString()
}

/**
 * Flow-context counterpart to [foldPlainScalarContinuation]. Unlike block context, a flow
 * collection isn't indentation-bounded once opened (delimited by its closing bracket/brace
 * instead), so there's no indentation threshold here — continuation is decided purely by
 * what the next line starts with, using the same terminators (`,`, `]`, `}`, a real `:` key
 * separator, or a whitespace-preceded `#` comment) that end a single-line flow scalar.
 *
 * Also called from [GhostYamlFlatReader.readKey] — a flow mapping key can fold across lines
 * like any other flow plain scalar.
 */
internal fun GhostYamlFlatReader.foldFlowPlainScalarContinuation(firstLine: String): String? {
    val localRawData = rawData
    val localLimit = limit
    val scalarEndPosition = position
    var folded: StringBuilder? = null
    var blankLines = 0

    while (true) {
        val beforeNewline = position
        val blankScan = scanFoldBlankLine(rawData = localRawData, position = position, limit = localLimit)
        if (blankScan.isBlank) {
            blankLines++
            position = blankScan.contentStart
            if (position >= localLimit) break
            continue
        }

        // No indentation threshold here (see KDoc above) — skip this line's leading
        // whitespace and look at what comes next.
        position = blankScan.contentStart
        val lineStart = position
        val leadByte = localRawData[position]
        val leadIsColonSeparator = isMappingColonAt(rawData = localRawData, position = position, limit = localLimit)
        val isTerminatorLine = leadByte == TOK.COMMA_BYTE || leadByte == TOK.RIGHT_BRACE_BYTE ||
            leadByte == TOK.RIGHT_BRACKET_BYTE || leadByte == TOK.HASH_BYTE || leadIsColonSeparator ||
            isDocumentMarker() || isDocumentEndMarker()
        if (isTerminatorLine) {
            // This line is nothing but the scalar's own terminator (or a comment) — not a
            // continuation. A comment can't appear inside a still-open fold, so a
            // comment-only line ends the scalar here too regardless of what follows
            // (case CML9). Same rewind rule as the block version.
            position = if (folded != null) beforeNewline else scalarEndPosition
            break
        }

        // Scan this line's content with the same stop rules a single-line flow scalar uses
        // (mirrors the scan at the top of readPlainScalarOrMapping).
        var scanPos = lineStart
        while (scanPos < localLimit) {
            val currentByte = localRawData[scanPos]
            when {
                currentByte == TOK.COLON_BYTE -> {
                    if (isMappingColonAt(rawData = localRawData, position = scanPos, limit = localLimit)) break
                    scanPos++
                }

                isLineBreakByte(byte = currentByte) -> break
                currentByte == TOK.HASH_BYTE -> {
                    if (scanPos > lineStart && isInlineWhitespaceByte(byte = localRawData[scanPos - 1])) break
                    scanPos++
                }

                isFlowScalarTerminatorByte(byte = currentByte) -> break
                else -> scanPos++
            }
        }

        val lineEnd = trimTrailingSpaces(start = lineStart, end = scanPos)
        val lineText = localRawData.decodeToString(lineStart, lineEnd)

        if (folded == null) folded = StringBuilder(firstLine)
        appendFoldedLine(builder = folded, blankLines = blankLines, lineText = lineText)
        blankLines = 0

        position = scanPos
        if (position >= localLimit) break
        // If the scan stopped at a mid-line terminator (not a newline), the scalar is done —
        // don't loop expecting another continuation line.
        if (!isLineBreakByte(byte = localRawData[position])) break
    }

    return folded?.toString()
}
