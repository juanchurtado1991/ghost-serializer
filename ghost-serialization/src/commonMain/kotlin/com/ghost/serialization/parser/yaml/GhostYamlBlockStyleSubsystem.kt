package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML block-style mappings and sequences.
 */

/**
 * Reads a block mapping starting at the current position.
 * Called when we detect "key: value" on a new line.
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
            // A tab can't be part of the indentation opening or extending a block mapping —
            // tabs have no fixed column width, so there's no way to compare this line's
            // indentation against blockIndent/a sibling's. Harmless once inside an already-
            // established scalar's own content (readValue never reaches this loop for that).
            if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_MAPPING_INDENT)

            if (isExplicitKeyIndicator()) {
                val (key, value) = readExplicitKeyEntry(blockIndent)
                result[key] = value
                continue
            }

            // Read key
            val key = readKey(inFlow = false) ?: break
            skipInlineWhitespace()

            // Expect ':' after the key
            if (position >= localLimit || localRawData[position] != C.COLON_BYTE) {
                yamlError("${C.ERR_EXPECTED_COLON_AFTER_KEY_PREFIX}$key${C.ERR_EXPECTED_COLON_AFTER_KEY_MID}$position")
            }
            position++ // consume ':'
            // An implicit "key: value" pair's value can't redirect into a nested block
            // mapping while still inline on the same physical line as this ':' (that shape is
            // only legal via YAML's "compact notation", reserved for explicit "?"/":" entries
            // — see resolveValueAfterColon's KDoc). Without this, "a: b: c: d" silently parsed
            // as {"a": {"b": {"c": "d"}}} instead of being rejected (yaml-test-suite ZCZ6).
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
 * determine and read the value. Shared by [readBlockMapping]'s implicit ("key: value")
 * entries and [readExplicitKeyEntry]'s explicit ("? key\n: value") entries — both need
 * exactly the same "same line, next line indented, or no value at all" resolution, but differ
 * on whether an *inline* value may itself redirect into a nested block mapping: explicit
 * entries get YAML's "compact notation" allowance (see the comment at
 * [readExplicitKeyEntry]'s call site), implicit ones
 * don't — [allowMappingRedirect] lets each caller opt in/out.
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
                    // foldIndent = blockIndent (not valueIndent): if this value turns out to
                    // be a plain scalar, later continuation lines only need to be indented
                    // more than the *enclosing mapping's* indent to keep folding — matching
                    // the inline-value case below — not more than this value's own
                    // auto-detected column, which is typically the SAME indent every
                    // continuation line also sits at (see 4CQQ/M5C3/NB6Z/RZT7/UGM3).
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
            // See the equivalent check in readBlockMapping — tabs can't be part of the
            // indentation opening or extending a block sequence.
            if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_SEQUENCE_INDENT)

            // Consume '-'
            position++ // '-'

            // Indentation of the element value is the position of '-' plus 2.
            val elementIndent = lineIndent + 2

            // Skip the optional inline space after '-'
            if (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
                position++
            }

            // A comment directly after "- " (e.g. "- # Empty") leaves no inline value, same
            // as it would after a mapping key's ':' (see resolveValueAfterColon) — without
            // this, the comment text would be read as the item's own plain-scalar content.
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
                        // Delegate to readValue's own dispatch — see the equivalent comment
                        // in readBlockMapping for why (bare scalar vs. mapping vs. block
                        // scalar, not just sequence-vs-mapping).
                        else readValue(itemIndent, inFlow = false)
                    }
                }

                else -> {
                    // Value starts on the same line after '- '
                    // Try reading plain scalar or mapping or sequence.
                    // Since we are parsing the list item, we can call readValue with elementIndent.
                    readValue(elementIndent, inFlow = false)
                }
            }
            result.add(item)
        }
    } finally {
        depth--
    }
    return result
}
