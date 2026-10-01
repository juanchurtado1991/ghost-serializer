package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK
import com.ghost.serialization.yaml.exception.GhostYamlException

/** Parses/manages YAML Anchors (&anchor), Aliases (*alias), and Merge Keys (<<). */

/**
 * Entry point for [GhostYamlFlatReader.readValue]'s `&` dispatch. A block-context anchor is
 * ambiguous on sight: it may anchor a *value* (`key: &a value`) or the *key* of an implicit
 * mapping entry (`&a a: &b b` — the anchor belongs to bare key "a", not the whole mapping), and
 * [readAnchoredValue] alone only handles the value shape.
 *
 * Resolved by speculatively reparsing with [GhostYamlFlatReader.readKey] (commit 9812d08c /
 * case SU74) to check for a following `:`, then rewinding and re-dispatching for real:
 * [GhostYamlFlatReader.readBlockMapping] if it looked like a key line, else [readAnchoredValue].
 * Flow context has no such ambiguity (a flow key is delimited by `{`/`,`/`}`, not indentation).
 */
internal fun GhostYamlFlatReader.readAnchoredValueOrMappingKey(
    indent: Int,
    inFlow: Boolean,
    strictDedent: Boolean
): Any? {
    if (inFlow) return readAnchoredValue(indent = indent, inFlow = inFlow, strictDedent = strictDedent)

    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Non-mutating lookahead: skip "&anchor name" + inline whitespace to see what follows the
    // anchor prefix, without touching `position` yet.
    var lookahead = startPosition + 1 // '&'
    while (lookahead < localLimit && !isAnchorNameTerminator(byte = localRawData[lookahead])) {
        lookahead++
    }
    while (lookahead < localLimit && isInlineWhitespaceByte(byte = localRawData[lookahead])) {
        lookahead++
    }
    // A flow collection right after the anchor can't safely go through readKey's plain-text scan:
    // it has no bracket-depth awareness, so "&ORIGIN {x: 73, y: 129}" would falsely look like a
    // key at its first *inner* ':' (case C4HZ). readKey handles a quoted scalar fine, so only
    // flow collections need excluding — readAnchoredValue's own readValue() dispatch
    // (readFlowCollectionOrMappingKey) correctly resolves those as key or value.
    val followedByFlowCollection = lookahead < localLimit &&
        (localRawData[lookahead] == TOK.LEFT_BRACE_BYTE || localRawData[lookahead] == TOK.LEFT_BRACKET_BYTE)

    val looksLikeMappingKey = !followedByFlowCollection && try {
        val key = readKey(inFlow = false)
        key != null && looksLikeMappingKeyColon()
    } catch (e: GhostYamlException) {
        // A legitimate anchored value that doesn't parse as a sensible key (e.g.
        // "&anchor:\n  nested: mapping") must fall through to readAnchoredValue cleanly,
        // not propagate this speculative attempt's error.
        false
    } finally {
        // Undo the peek — readBlockMapping/readAnchoredValue below re-reads this text for real.
        // (readKey may have already bound the anchor as a side effect of the peek; harmless,
        // since whichever real path runs next overwrites it with the correct binding.)
        position = startPosition
    }

    return if (looksLikeMappingKey) {
        readBlockMapping(blockIndent = indent.coerceAtLeast(0))
    } else {
        readAnchoredValue(indent = indent, inFlow = inFlow, strictDedent = strictDedent)
    }
}

/** Whether [byte] terminates an anchor/alias name (whitespace, newline, or a flow delimiter). */
private fun isAnchorNameTerminator(byte: Byte): Boolean =
    byte == TOK.SPACE_BYTE || byte == TOK.TAB_BYTE || byte == TOK.NEWLINE_BYTE || byte == TOK.CR_BYTE ||
        byte == TOK.COMMA_BYTE || byte == TOK.RIGHT_BRACE_BYTE || byte == TOK.RIGHT_BRACKET_BYTE

/** Whether the byte at [GhostYamlFlatReader.position] is a `:` that ends a mapping key (not part of a scalar). */
private fun GhostYamlFlatReader.looksLikeMappingKeyColon(): Boolean {
    val localLimit = limit
    val localRawData = rawData
    return position < localLimit && localRawData[position] == TOK.COLON_BYTE &&
        (position + 1 >= localLimit ||
            localRawData[position + 1] == TOK.SPACE_BYTE ||
            localRawData[position + 1] == TOK.NEWLINE_BYTE ||
            localRawData[position + 1] == TOK.CR_BYTE ||
            localRawData[position + 1] == TOK.TAB_BYTE)
}

internal fun GhostYamlFlatReader.readAnchoredValue(indent: Int, inFlow: Boolean, strictDedent: Boolean): Any? {
    position++ // consume '&'
    val localRawData = rawData
    val localLimit = limit

    val start = position
    while (position < localLimit && !isAnchorNameTerminator(byte = localRawData[position])) {
        position++
    }

    val anchorName = localRawData.decodeToString(start, position)

    // Skip inline whitespace, then a same-line trailing comment (e.g. "top: &node # comment") —
    // leaves no inline value, same as a bare newline would.
    skipInlineWhitespace()
    if (position < localLimit && localRawData[position] == TOK.HASH_BYTE) {
        skipToEndOfLine()
    }

    // An anchor can't directly wrap an alias reference — it anchors actual node content, not
    // a reference to something else.
    val isAliasAtPosition = !inFlow && position < localLimit && localRawData[position] == TOK.ASTERISK_BYTE
    if (isAliasAtPosition) {
        yamlError(message = "${EM.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${EM.ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX}")
    }
    // Nor can a block sequence entry start inline on the same line — "&anchor - item" is
    // invalid, the "-" needs its own line.
    val isBlockSequenceDash = !inFlow &&
        position < localLimit &&
        localRawData[position] == TOK.DASH_BYTE &&
        isBlockSequenceEntry()
    if (isBlockSequenceDash) {
        yamlError(message = "${EM.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${EM.ERR_ANCHOR_FOLLOWED_BY_SEQ_SUFFIX}")
    }

    val positionBeforeLineBreak = position
    val isAtLineBreak = position < localLimit &&
        (localRawData[position] == TOK.NEWLINE_BYTE || localRawData[position] == TOK.CR_BYTE)
    val value =
        if (isAtLineBreak) {
            advanceLine()
            skipWhitespaceAndComments()
            val nextLineIndent = currentIndent
            val continuesAsSequenceEntry =
                position < localLimit && localRawData[position] == TOK.DASH_BYTE && isBlockSequenceEntry()
            // Mirrors readBlockMapping/readBlockSequence's "is there nested content" check: a
            // mapping value must indent *more* than its key (strictDedent), a sequence item's
            // inline value may continue at exactly its element indent (not strictDedent).
            val isDedent = if (strictDedent) nextLineIndent <= indent else nextLineIndent < indent
            val endsBlockContext = !inFlow && (position >= localLimit || (isDedent && !continuesAsSequenceEntry))
            if (endsBlockContext) {
                // Next line dedents back to a sibling (or nothing's left) — this anchor's value
                // is empty/null. Rewind past the line break so the caller's loop sees that line
                // fresh, same as a plain "key:" with no value.
                position = positionBeforeLineBreak
                null
            } else {
                readValue(nextLineIndent, inFlow)
            }
        } else {
            readValue(indent, inFlow)
        }
    anchorTable[anchorName] = value
    return value
}

/**
 * Reads an alias, then checks whether a `:` follows: an alias's resolved value can itself be a
 * block-mapping key (e.g. `top3: &node3\n  *alias1 : scalar3`), not just a value.
 */
internal fun GhostYamlFlatReader.readAliasOrMappingKey(indent: Int, inFlow: Boolean): Any? {
    val startPosition = position
    val value = readAlias()
    if (inFlow) return value
    skipInlineWhitespace()
    if (!looksLikeMappingKeyColon()) return value
    position = startPosition
    return readBlockMapping(blockIndent = indent.coerceAtLeast(0))
}

internal fun GhostYamlFlatReader.readAlias(): Any? {
    position++ // consume '*'
    val localRawData = rawData
    val localLimit = limit

    val start = position
    while (position < localLimit && !isAnchorNameTerminator(byte = localRawData[position])) {
        position++
    }

    val aliasName = localRawData.decodeToString(start, position)
    // anchorTable[aliasName] ?: error(...) would be wrong: a Map lookup returns null both when
    // the key is absent and when present with a null value (e.g. "a: &anchor\nb: *anchor"),
    // so the two cases must be told apart explicitly.
    if (!anchorTable.containsKey(key = aliasName)) {
        yamlError(message = "${EM.ERR_ANCHOR_NOT_FOUND_PREFIX}$aliasName${EM.ERR_ANCHOR_NOT_FOUND_SUFFIX}")
    }
    return anchorTable[aliasName]
}

internal fun GhostYamlFlatReader.mergeInto(target: MutableMap<String, Any?>, value: Any?) {
    when (value) {
        is Map<*, *> -> {
            for ((k, v) in value) {
                val keyStr = k as? String ?: continue
                if (!target.containsKey(key = keyStr)) {
                    target[keyStr] = v
                }
            }
        }

        is List<*> -> {
            var index = 0
            val size = value.size
            while (index < size) {
                mergeInto(target = target, value = value[index])
                index++
            }
        }
    }
}
