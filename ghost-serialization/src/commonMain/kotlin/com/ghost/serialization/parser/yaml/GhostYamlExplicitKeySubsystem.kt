package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Handles YAML's explicit block-mapping keys (`? key` / `: value`). Unlike an implicit
 * "key: value" pair, the key may be any node — not just plain scalar text — and the value is
 * entirely optional; a bare `? key` maps to null.
 */

/** True if position is at a `?` explicit-key indicator (must be followed by whitespace/EOL). */
internal fun GhostYamlFlatReader.isExplicitKeyIndicator(): Boolean {
    if (position >= limit || rawData[position] != C.QUESTION_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}

/**
 * Reads one "? key" / ": value" entry, with [GhostYamlFlatReader.position] at the "?". If a ":"
 * doesn't follow at [blockIndent], the value is null and nothing past the key is consumed.
 */
internal fun GhostYamlFlatReader.readExplicitKeyEntry(blockIndent: Int): Pair<String, Any?> {
    position++ // consume '?'
    skipInlineWhitespace()
    val localRawData = rawData
    val localLimit = limit

    val keyNode = if (position >= localLimit || localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE) {
        // Key on later line(s): mirrors value-after-':' resolution, including the exception
        // that a '-' sequence entry may sit at exactly blockIndent (e.g. "?\n- a\n- b").
        advanceLine()
        skipWhitespaceAndComments()
        val continuesAsSequenceEntry =
            position < localLimit && localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry()
        if (position >= localLimit || (currentIndent <= blockIndent && !(currentIndent == blockIndent && continuesAsSequenceEntry))) {
            null
        } else {
            readValue(currentIndent, inFlow = false)
        }
    } else if (isExplicitValueIndicator()) {
        // Nothing between '?' and ':' (e.g. "? : x") — an empty/null key.
        null
    } else {
        // Unlike an implicit pair's value, explicit-key content supports YAML's "compact
        // notation" — a nested block mapping/sequence starting inline right after "?"/":"
        // (spec example 8.19). allowMappingRedirect stays at its default (true) here.
        readValue(blockIndent, inFlow = false, strictDedent = true)
    }
    val key = stringifyExplicitMappingKey(keyNode)

    // Look for ':'. On the key's own line it's always valid regardless of column; the
    // indentation check only matters once we've crossed onto a later line.
    val positionBeforeGap = position
    skipWhitespaceAndComments()
    var crossedLine = false
    var scanPos = positionBeforeGap
    while (scanPos < position) {
        if (localRawData[scanPos] == C.NEWLINE_BYTE || localRawData[scanPos] == C.CR_BYTE) {
            crossedLine = true
            break
        }
        scanPos++
    }
    val value = if (position < localLimit && isExplicitValueIndicator() && (!crossedLine || currentIndent == blockIndent)) {
        position++ // consume ':'
        resolveValueAfterColon(blockIndent)
    } else {
        null
    }
    return key to value
}

/** True if position is at a `:` explicit-value indicator (must be followed by whitespace/EOL). */
private fun GhostYamlFlatReader.isExplicitValueIndicator(): Boolean {
    if (rawData[position] != C.COLON_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}

/**
 * Converts a node read as an explicit key into the String [GhostYamlFlatReader]'s
 * `Map<String, Any?>` representation needs. Non-scalar keys have no clean string form, but no
 * yaml-test-suite case using one has a JSON fixture to match against either.
 */
internal fun stringifyExplicitMappingKey(keyNode: Any?): String = when (keyNode) {
    null -> ""
    is String -> keyNode
    else -> keyNode.toString()
}
