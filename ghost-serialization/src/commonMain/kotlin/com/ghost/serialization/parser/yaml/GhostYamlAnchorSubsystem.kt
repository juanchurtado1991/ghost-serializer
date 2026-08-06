package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing and managing YAML Anchors (&anchor), Aliases (*alias), and Merge Keys (<<).
 */

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
        yamlError("Anchor '$anchorName' cannot be immediately followed by an alias")
    }
    // Nor can a block sequence entry start inline right after it on the same line — "&anchor -
    // item" isn't a valid way to anchor a sequence, the "-" needs its own line.
    if (!inFlow && position < localLimit && localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry()) {
        yamlError("Anchor '$anchorName' cannot be immediately followed by a block sequence entry on the same line")
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
        yamlError("Anchor '$aliasName' not found")
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
            var idx = 0
            val size = value.size
            while (idx < size) {
                mergeInto(target, value[idx])
                idx++
            }
        }
    }
}
