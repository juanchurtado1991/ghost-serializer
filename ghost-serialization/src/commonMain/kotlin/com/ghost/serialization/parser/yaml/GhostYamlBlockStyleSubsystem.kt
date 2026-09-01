package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML block-style mappings and sequences.
 */

/**
 * Reads a block mapping starting at the current position ("key: value" on a new line).
 *
 * @param blockIndent The indentation of the first key in this mapping.
 */
internal fun GhostYamlFlatReader.readBlockMapping(blockIndent: Int): Map<String, Any?> {
    if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
    depth++
    val result = LinkedHashMap<String, Any?>(C.DEFAULT_MAP_CAPACITY)
    val localLimit = limit
    val localRawData = rawData
    try {
        while (position < localLimit) {
            skipWhitespaceAndComments()
            if (position >= localLimit) break

            val lineIndent = currentIndent
            if (result.isNotEmpty() && lineIndent < blockIndent) break  // dedented — end of this mapping
            if (isDocumentMarker() || isDocumentEndMarker()) break
            // Tabs have no fixed column width, so they can't open/extend a block mapping's
            // indentation — harmless once inside an already-established scalar's own content.
            if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_MAPPING_INDENT)

            if (isExplicitKeyIndicator()) {
                val (key, value) = readExplicitKeyEntry(blockIndent)
                result[key] = value
                continue
            }

            val key = readKey(inFlow = false) ?: break
            skipInlineWhitespace()

            if (position >= localLimit || localRawData[position] != C.COLON_BYTE) {
                yamlError("${C.ERR_EXPECTED_COLON_AFTER_KEY_PREFIX}$key${C.ERR_EXPECTED_COLON_AFTER_KEY_MID}$position")
            }
            position++ // consume ':'
            // An implicit pair's value can't redirect into a nested mapping while still inline
            // on this line — that's only legal via "compact notation" (explicit "?"/":" entries).
            // Without this, "a: b: c: d" would parse instead of being rejected (yaml-test-suite ZCZ6).
            val value = resolveValueAfterColon(blockIndent, allowMappingRedirect = false)

            if (key == C.STR_MERGE_KEY) {
                mergeInto(result, value)
            } else {
                result[key] = value
            }
        }
    } finally {
        depth--
    }
    return result
}

/**
 * Called right after a mapping ':' has been consumed and inline whitespace skipped, to
 * determine and read the value. Shared by [readBlockMapping]'s implicit entries and
 * [readExplicitKeyEntry]'s explicit ones, which differ on whether an *inline* value may
 * redirect into a nested block mapping (YAML's "compact notation") — [allowMappingRedirect]
 * lets each caller opt in/out.
 */
internal fun GhostYamlFlatReader.resolveValueAfterColon(blockIndent: Int, allowMappingRedirect: Boolean = true): Any? {
    skipInlineWhitespace()
    val localLimit = limit
    val localRawData = rawData
    if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
        skipToEndOfLine()
    }
    return when {
        position >= localLimit -> null
        localRawData[position] == C.NEWLINE_BYTE ||
                localRawData[position] == C.CR_BYTE -> {
            advanceLine()
            skipWhitespaceAndComments()
            if (position >= localLimit) null
            else {
                val valueIndent = currentIndent
                if (valueIndent < blockIndent) {
                    null
                } else if (valueIndent == blockIndent && !(localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry())) {
                    null
                } else {
                    // foldIndent = blockIndent (not valueIndent): a plain-scalar continuation
                    // line only needs to be indented past the *enclosing mapping's* indent to
                    // keep folding, not past this value's own auto-detected column (see
                    // 4CQQ/M5C3/NB6Z/RZT7/UGM3).
                    readValue(valueIndent, inFlow = false, foldIndent = blockIndent)
                }
            }
        }

        else -> readValue(blockIndent, inFlow = false, strictDedent = true, allowMappingRedirect = allowMappingRedirect)
    }
}

/**
 * Reads a block sequence (list). Each item starts with '- '.
 *
 * @param seqIndent Indentation of the '-' markers.
 */
internal fun GhostYamlFlatReader.readBlockSequence(seqIndent: Int): List<Any?> {
    if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
    depth++
    val result = mutableListOf<Any?>()
    val localLimit = limit
    val localRawData = rawData
    try {
        while (position < localLimit) {
            skipWhitespaceAndComments()
            if (position >= localLimit) break

            val lineIndent = currentIndent
            if (result.isNotEmpty() && lineIndent < seqIndent) break
            if (!isBlockSequenceEntry()) break
            if (isDocumentMarker()) break
            // See the equivalent tab check in readBlockMapping.
            if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_SEQUENCE_INDENT)

            position++ // '-'

            // Element value indent is the '-' column plus 2 (the dash and its following space).
            val elementIndent = lineIndent + 2

            if (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
                position++
            }

            // A comment directly after "- " (e.g. "- # Empty") leaves no inline value, same
            // as after a mapping key's ':' (see resolveValueAfterColon).
            if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
                skipToEndOfLine()
            }

            val item: Any? = when {
                position >= localLimit -> null
                localRawData[position] == C.NEWLINE_BYTE ||
                        localRawData[position] == C.CR_BYTE -> {
                    advanceLine()
                    skipWhitespaceAndComments()
                    if (position >= localLimit) null
                    else {
                        val itemIndent = currentIndent
                        if (itemIndent < elementIndent) null
                        // Delegate to readValue's own dispatch (bare scalar vs. mapping vs.
                        // block scalar) — see the equivalent comment in readBlockMapping.
                        else readValue(itemIndent, inFlow = false)
                    }
                }

                else -> readValue(elementIndent, inFlow = false)
            }
            result.add(item)
        }
    } finally {
        depth--
    }
    return result
}
