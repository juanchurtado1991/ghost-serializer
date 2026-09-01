package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.exception.GhostYamlException

/** Parses/manages YAML Anchors (&anchor), Aliases (*alias), and Merge Keys (<<). */

/**
 * Entry point for [GhostYamlFlatReader.readValue]'s `&` dispatch. A block-context anchor is
 * ambiguous on sight: it may anchor a *value* (`key: &a value`) or the *key* of an implicit
 * mapping entry (`&a a: &b b` — the anchor belongs to bare key "a", not the whole mapping).
 * [readAnchoredValue] alone only handles the value shape: if the text after the anchor looks
 * like a key, it recurses into [GhostYamlFlatReader.readBlockMapping], which greedily consumes
 * every sibling entry at that indent, binding the anchor to the whole resulting map instead of
 * just the key.
 *
 * This speculatively re-parses the anchor + following text with [GhostYamlFlatReader.readKey]
 * (reusing its anchor-on-key binding — commit 9812d08c / case SU74), checks for a following `:`,
 * then rewinds and re-dispatches for real: [GhostYamlFlatReader.readBlockMapping] if it looked
 * like a key line, else [readAnchoredValue]. Flow context has no such ambiguity (a flow mapping
 * key is delimited by `{`/`,`/`}`, not indentation), so it's untouched here.
 */
internal fun GhostYamlFlatReader.readAnchoredValueOrMappingKey(indent: Int, inFlow: Boolean, strictDedent: Boolean): Any? {
    if (inFlow) return readAnchoredValue(indent, inFlow, strictDedent)

    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Non-mutating lookahead: skip "&anchorname" + inline whitespace to see what follows the
    // anchor prefix, without touching `position` yet.
    var lookahead = startPosition + 1 // '&'
    while (lookahead < localLimit) {
        val tokenByte = localRawData[lookahead]
        if (tokenByte == C.SPACE_BYTE || tokenByte == C.TAB_BYTE || tokenByte == C.NEWLINE_BYTE ||
            tokenByte == C.CR_BYTE || tokenByte == C.COMMA_BYTE ||
            tokenByte == C.RIGHT_BRACE_BYTE || tokenByte == C.RIGHT_BRACKET_BYTE
        ) break
        lookahead++
    }
    while (lookahead < localLimit && (localRawData[lookahead] == C.SPACE_BYTE || localRawData[lookahead] == C.TAB_BYTE)) {
        lookahead++
    }
    // A flow collection right after the anchor can't safely go through readKey's plain-text scan:
    // it has no bracket-depth awareness, so "&ORIGIN {x: 73, y: 129}" would falsely look like a
    // key at its first *inner* ':' (case C4HZ). readKey handles a quoted scalar fine, so only
    // flow collections need excluding — readAnchoredValue's own readValue() dispatch
    // (readFlowCollectionOrMappingKey) correctly resolves those as key or value.
    val followedByFlowCollection = lookahead < localLimit &&
        (localRawData[lookahead] == C.LEFT_BRACE_BYTE || localRawData[lookahead] == C.LEFT_BRACKET_BYTE)

    val looksLikeMappingKey = !followedByFlowCollection && try {
        val key = readKey(inFlow = false)
        key != null && position < localLimit && localRawData[position] == C.COLON_BYTE &&
            (position + 1 >= localLimit ||
                localRawData[position + 1] == C.SPACE_BYTE ||
                localRawData[position + 1] == C.NEWLINE_BYTE ||
                localRawData[position + 1] == C.CR_BYTE ||
                localRawData[position + 1] == C.TAB_BYTE)
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

    return if (looksLikeMappingKey) readBlockMapping(indent.coerceAtLeast(0)) else readAnchoredValue(indent, inFlow, strictDedent)
}

internal fun GhostYamlFlatReader.readAnchoredValue(indent: Int, inFlow: Boolean, strictDedent: Boolean): Any? {
    position++ // consume '&'
    val localRawData = rawData
    val localLimit = limit

    val start = position
    while (position < localLimit) {
        val currByte = localRawData[position]
        if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE ||
            currByte == C.COMMA_BYTE || currByte == C.RIGHT_BRACE_BYTE || currByte == C.RIGHT_BRACKET_BYTE
        ) break
        position++
    }

    val anchorName = localRawData.decodeToString(start, position)

    // Skip inline whitespace, then a same-line trailing comment (e.g. "top: &node # comment") —
    // leaves no inline value, same as a bare newline would.
    skipInlineWhitespace()
    if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
        skipToEndOfLine()
    }

    // An anchor can't directly wrap an alias reference — it anchors actual node content, not
    // a reference to something else.
    if (!inFlow && position < localLimit && localRawData[position] == C.ASTERISK_BYTE) {
        yamlError("${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX}")
    }
    // Nor can a block sequence entry start inline on the same line — "&anchor - item" is
    // invalid, the "-" needs its own line.
    if (!inFlow && position < localLimit && localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry()) {
        yamlError("${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${C.ERR_ANCHOR_FOLLOWED_BY_SEQ_SUFFIX}")
    }

    val positionBeforeLineBreak = position
    val value =
        if (position < localLimit && (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)) {
            advanceLine()
            skipWhitespaceAndComments()
            val nextLineIndent = currentIndent
            val continuesAsSequenceEntry =
                position < localLimit && localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry()
            // Mirrors readBlockMapping/readBlockSequence's "is there nested content" check: a
            // mapping value must indent *more* than its key (strictDedent), a sequence item's
            // inline value may continue at exactly its element indent (not strictDedent).
            val isDedent = if (strictDedent) nextLineIndent <= indent else nextLineIndent < indent
            if (!inFlow && (position >= localLimit || (isDedent && !continuesAsSequenceEntry))) {
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
 * Reads an alias via [readAlias], then checks whether a `:` follows: an alias can itself be a
 * block-mapping key (e.g. `top3: &node3\n  *alias1 : scalar3`, where `*alias1`'s resolved value
 * becomes the key), not just a value. Without this, [GhostYamlFlatReader.readValue]'s `*`
 * dispatch would read only the alias as a complete value and mishandle the trailing
 * `: scalar3` instead of nesting it under this key.
 */
internal fun GhostYamlFlatReader.readAliasOrMappingKey(indent: Int, inFlow: Boolean): Any? {
    val startPosition = position
    val value = readAlias()
    if (inFlow) return value
    skipInlineWhitespace()
    val localLimit = limit
    val localRawData = rawData
    val isMappingKey = position < localLimit && localRawData[position] == C.COLON_BYTE &&
        (position + 1 >= localLimit ||
            localRawData[position + 1] == C.SPACE_BYTE ||
            localRawData[position + 1] == C.NEWLINE_BYTE ||
            localRawData[position + 1] == C.CR_BYTE ||
            localRawData[position + 1] == C.TAB_BYTE)
    if (!isMappingKey) return value
    position = startPosition
    return readBlockMapping(indent.coerceAtLeast(0))
}

internal fun GhostYamlFlatReader.readAlias(): Any? {
    position++ // consume '*'
    val localRawData = rawData
    val localLimit = limit

    val start = position
    while (position < localLimit) {
        val currByte = localRawData[position]
        if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE ||
            currByte == C.COMMA_BYTE || currByte == C.RIGHT_BRACE_BYTE || currByte == C.RIGHT_BRACKET_BYTE
        ) break
        position++
    }

    val aliasName = localRawData.decodeToString(start, position)
    // anchorTable[aliasName] ?: error(...) would be wrong: a Map lookup returns null both when
    // the key is absent and when present with a null value (e.g. "a: &anchor\nb: *anchor"),
    // so the two cases must be told apart explicitly.
    if (!anchorTable.containsKey(aliasName)) {
        yamlError("${C.ERR_ANCHOR_NOT_FOUND_PREFIX}$aliasName${C.ERR_ANCHOR_NOT_FOUND_SUFFIX}")
    }
    return anchorTable[aliasName]
}

internal fun GhostYamlFlatReader.mergeInto(target: MutableMap<String, Any?>, value: Any?) {
    when (value) {
        is Map<*, *> -> {
            for ((k, v) in value) {
                val keyStr = k as? String ?: continue
                if (!target.containsKey(keyStr)) {
                    target[keyStr] = v
                }
            }
        }

        is List<*> -> {
            var index = 0
            val size = value.size
            while (index < size) {
                mergeInto(target, value[index])
                index++
            }
        }
    }
}
