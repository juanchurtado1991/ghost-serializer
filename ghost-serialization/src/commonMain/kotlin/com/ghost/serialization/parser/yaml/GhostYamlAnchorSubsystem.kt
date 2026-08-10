package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.exception.GhostYamlException

/**
 * Subsystem for parsing and managing YAML Anchors (&anchor), Aliases (*alias), and Merge Keys (<<).
 */

/**
 * Entry point for [GhostYamlFlatReader.readValue]'s `&` dispatch. An anchor at the start of a
 * block-context line is ambiguous on sight: it may anchor a *value* (`key: &a value`, a bare
 * `&a value` sequence item) or it may anchor the *key* of an implicit mapping entry (`&a a: &b b`
 * — the anchor belongs to the bare scalar key "a", not to the "a: &b b" mapping as a whole).
 * [readAnchoredValue] alone only handles the first shape — it recurses into [GhostYamlFlatReader.readValue]
 * for its "value", and if that redirects into [GhostYamlFlatReader.readBlockMapping] (because the
 * text after the anchor looks like a key), the mapping greedily consumes every sibling entry at
 * that indent before ever returning, so the anchor ends up bound to the *whole resulting map*
 * instead of the bare key.
 *
 * This speculatively re-parses the anchor + following text as [GhostYamlFlatReader.readKey] would
 * (reusing its already-correct anchor-on-key binding — see commit 9812d08c / case SU74 — rather
 * than duplicating that scan here), checks whether a `:` key separator follows, then always
 * rewinds and re-dispatches for real: to [GhostYamlFlatReader.readBlockMapping] if it looked like
 * a key line, or to the ordinary [readAnchoredValue] otherwise. Flow context has no such ambiguity
 * (a flow mapping key is delimited by `{`/`,`/`}`, not indentation), so it's left untouched.
 */
internal fun GhostYamlFlatReader.readAnchoredValueOrMappingKey(indent: Int, inFlow: Boolean, strictDedent: Boolean): Any? {
    if (inFlow) return readAnchoredValue(indent, inFlow, strictDedent)

    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Non-mutating lookahead: skip "&anchorname" + inline whitespace (mirroring readAnchoredValue's
    // own anchor-name scan) to see what immediately follows the anchor prefix, without touching
    // `position` yet.
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
    // A flow collection right after the anchor is a case readKey's plain-text scan below can't
    // safely evaluate: it has no bracket-depth awareness, so "&ORIGIN {x: 73, y: 129}" would
    // falsely look like a key at its own first *inner* ':' (case C4HZ). readKey handles a quoted
    // scalar here just fine (it has its own dedicated branch for that), so only flow collections
    // need to be excluded — when this anchor turns out to actually wrap a flow-collection value,
    // readAnchoredValue's own readValue() dispatch (readFlowCollectionOrMappingKey) already
    // determines correctly whether that's a key or a value.
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
        // A legitimate anchored value that doesn't happen to parse as a sensible key (e.g.
        // "&anchor:\n  nested: mapping" tripping readKey's own validation) must fall through to
        // readAnchoredValue cleanly, not propagate this speculative attempt's error.
        false
    } finally {
        // Undo the peek regardless of outcome — readBlockMapping/readAnchoredValue below re-reads
        // this text for real. (readKey may have already bound the anchor into anchorTable as a
        // side effect of the peek itself; that's harmless, since whichever real path runs next
        // unconditionally overwrites it with the correct binding before anything else can observe it.)
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
    // that leaves no inline value either, same as a bare newline would.
    skipInlineWhitespace()
    if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
        skipToEndOfLine()
    }

    // An anchor can't directly wrap an alias reference — anchoring applies to actual node
    // content, not to a reference to something else.
    if (!inFlow && position < localLimit && localRawData[position] == C.ASTERISK_BYTE) {
        yamlError("${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX}")
    }
    // Nor can a block sequence entry start inline right after it on the same line — "&anchor -
    // item" isn't a valid way to anchor a sequence, the "-" needs its own line.
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
            // Mirrors readBlockMapping/readBlockSequence's own "is there really nested content
            // here" check for an unanchored value at this same position: a mapping value must be
            // indented *more* than its key (strictDedent), while a sequence item's inline value
            // may continue at exactly its element indent (not strictDedent) — see the call sites.
            val isDedent = if (strictDedent) nextLineIndent <= indent else nextLineIndent < indent
            if (!inFlow && (position >= localLimit || (isDedent && !continuesAsSequenceEntry))) {
                // The next line dedents back to a sibling (or there's nothing left) — this
                // anchor's own value is empty/null. Rewind past just the line break so the
                // caller's loop sees that line fresh, the same way a plain "key:" with no value
                // leaves it for the next iteration to process as the next key.
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
 * Reads an alias via [readAlias], then — mirroring [GhostYamlFlatReader.readQuotedScalarOrMappingKey]
 * — checks whether a `:` follows: an alias can itself be a block-mapping key (e.g.
 * `top3: &node3\n  *alias1 : scalar3`, where the resolved value of `*alias1` becomes the key),
 * not just a value. Without this, [GhostYamlFlatReader.readValue]'s `*` dispatch would read only
 * the alias itself as a complete value and choke on (or silently misplace) the trailing
 * `: scalar3` as an unrelated sibling entry instead of nesting it under this key.
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
    // anchorTable[aliasName] ?: error(...) would be wrong here: a Map lookup returns null both
    // when the key is absent *and* when it's present with a null value (e.g. an anchor on an
    // empty node, "a: &anchor\nb: *anchor"), so the two cases must be told apart explicitly.
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
