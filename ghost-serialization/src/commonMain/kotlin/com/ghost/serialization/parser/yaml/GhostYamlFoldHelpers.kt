package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Result of consuming a newline and scanning whether the following line is blank
 * (whitespace-only until newline/EOF).
 *
 * @property afterNewline Position of the first byte of the line (after CR/LF).
 * @property contentStart First non-space/tab byte on the line, or past whitespace if blank.
 * @property isBlank True when the line is empty or whitespace-only through newline/EOF.
 */
internal class FoldBlankLineScan(
    val afterNewline: Int,
    val contentStart: Int,
    val isBlank: Boolean,
)

/**
 * Advances past a CR/LF at [position], then scans leading spaces/tabs to decide blank-ness.
 * Does not mutate caller state — wrappers assign [position] from the result.
 */
internal fun scanFoldBlankLine(rawData: ByteArray, position: Int, limit: Int): FoldBlankLineScan {
    var afterNewline = position
    if (afterNewline < limit && rawData[afterNewline] == C.CR_BYTE) afterNewline++
    if (afterNewline < limit && rawData[afterNewline] == C.NEWLINE_BYTE) afterNewline++

    var contentStart = afterNewline
    while (contentStart < limit &&
        (rawData[contentStart] == C.SPACE_BYTE || rawData[contentStart] == C.TAB_BYTE)
    ) {
        contentStart++
    }
    val isBlank = contentStart >= limit ||
        rawData[contentStart] == C.NEWLINE_BYTE || rawData[contentStart] == C.CR_BYTE
    return FoldBlankLineScan(afterNewline, contentStart, isBlank)
}

/**
 * Appends one folded continuation line using YAML plain-scalar folding rules:
 * blank lines become `\n` separators; otherwise a single space joins lines.
 */
internal fun appendFoldedLine(builder: StringBuilder, blankLines: Int, lineText: String) {
    if (blankLines > 0) repeat(blankLines) { builder.append('\n') } else builder.append(' ')
    builder.append(lineText)
}
