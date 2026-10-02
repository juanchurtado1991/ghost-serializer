package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/** Reads a mapping key: a plain scalar ending at ':', or a quoted string. */
internal fun GhostYamlFlatReader.readKey(inFlow: Boolean): String? {
    skipInlineWhitespace()
    val localLimit = limit
    val localRawData = rawData
    if (position >= localLimit) return null

    // Alias as key: "*name" resolves immediately to the aliased node's value, stringified the
    // same way an explicit-key node would be. Only recognized with no anchor/tag prefix
    // before it; combining both has no clear meaning and isn't exercised by any test.
    if (localRawData[position] == TOK.ASTERISK_BYTE) {
        return stringifyExplicitMappingKey(keyNode = readAlias())
    }

    val (anchorName, hadPrefixes) = consumeKeyAnchorAndTagPrefixes()
    // Having consumed an anchor/tag prefix commits us to a real key afterward — silently
    // returning null after eating those bytes would let a dangling anchor/tag masquerade as
    // "no more keys" instead of erroring.
    if (position >= localLimit) {
        if (hadPrefixes) yamlError(message = EM.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
        return null
    }
    // An anchor can't wrap an alias reference here either — same rule readAnchoredValue
    // enforces for values (an alias points at an existing node, not one a new anchor can attach to).
    if (anchorName != null && localRawData[position] == TOK.ASTERISK_BYTE) {
        yamlError(message = "${EM.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${EM.ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX}")
    }
    val key = when (localRawData[position]) {
        TOK.DOUBLE_QUOTE_BYTE -> readQuotedKeyRejectingMultiLine(inFlow = inFlow) { readDoubleQuotedString() }
        TOK.SINGLE_QUOTE_BYTE -> readQuotedKeyRejectingMultiLine(inFlow = inFlow) { readSingleQuotedString() }
        else -> readBareKeyOrEmptyString(inFlow = inFlow, hadPrefixes = hadPrefixes)
    }
    if (key != null && anchorName != null) {
        anchorTable[anchorName] = key
    }
    return key
}

/**
 * Consumes any `&anchor`/`!tag` prefixes before a key (e.g. "&a5 !!str key5:"). A tag has no
 * JSON representation on a key, so it's dropped like an ordinary tagged value's tag; an anchor
 * still needs binding to the key's text so later aliases can resolve it. Returns the bound
 * anchor name (if any) alongside whether any prefix was consumed at all, since the caller
 * needs that to tell "no more keys" apart from "a dangling anchor/tag with no key after it".
 */
private fun GhostYamlFlatReader.consumeKeyAnchorAndTagPrefixes(): Pair<String?, Boolean> {
    val localLimit = limit
    val localRawData = rawData
    var anchorName: String? = null
    val positionBeforePrefixes = position
    while (position < localLimit &&
        (localRawData[position] == TOK.AMPERSAND_BYTE || localRawData[position] == TOK.EXCLAMATION_BYTE)
    ) {
        val isAnchor = localRawData[position] == TOK.AMPERSAND_BYTE
        if (isAnchor) position++ // consume '&'
        val prefixStart = position
        while (position < localLimit) {
            val prefixByte = localRawData[position]
            val isDelimiterPrefix = prefixByte == TOK.SPACE_BYTE ||
                prefixByte == TOK.TAB_BYTE ||
                prefixByte == TOK.NEWLINE_BYTE ||
                prefixByte == TOK.CR_BYTE ||
                prefixByte == TOK.COMMA_BYTE ||
                prefixByte == TOK.RIGHT_BRACE_BYTE ||
                prefixByte == TOK.RIGHT_BRACKET_BYTE
            if (isDelimiterPrefix) break
            position++
        }
        if (isAnchor) anchorName = localRawData.decodeToString(prefixStart, position)
        skipInlineWhitespace()
    }
    return anchorName to (position != positionBeforePrefixes)
}

/** Scans a bare (unquoted) key ending at `:`/EOL, or resolves the empty-string-key shape (`": value"`). */
private fun GhostYamlFlatReader.readBareKeyOrEmptyString(inFlow: Boolean, hadPrefixes: Boolean): String? {
    val localLimit = limit
    val localRawData = rawData
    val startPosition = position
    while (position < localLimit) {
        val currentByte = localRawData[position]
        val isMappingColon = currentByte == TOK.COLON_BYTE &&
            isMappingColonAt(rawData = localRawData, position = position, limit = localLimit)
        if (isMappingColon) break
        if (isLineBreakByte(byte = currentByte)) break
        // An inline comment ends the key like it ends a plain scalar value, only
        // when preceded by whitespace: "a#b" stays one key, "a #b" ends at "a".
        val isWhitespacePrecededComment = currentByte == TOK.HASH_BYTE && position > startPosition &&
            isInlineWhitespaceByte(byte = localRawData[position - 1])
        if (isWhitespacePrecededComment) break
        if (inFlow && isFlowScalarTerminatorByte(byte = currentByte)) break
        position++
    }
    val endPosition = trimTrailingSpaces(start = startPosition, end = position)
    if (endPosition == startPosition) {
        // A bare ':' with nothing before it is a valid empty-string key (e.g.
        // ": value") — the loop above breaks on the first byte without advancing.
        // A prefix followed directly by ':' is this same empty-key shape, not
        // dangling. Anything else (newline, EOF) is "no more mapping to read", or a
        // genuinely dangling prefix if one was consumed.
        return if (position < localLimit && localRawData[position] == TOK.COLON_BYTE) {
            ""
        } else if (hadPrefixes) {
            yamlError(message = EM.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
        } else {
            null
        }
    }
    val firstLine = localRawData.decodeToString(startPosition, endPosition)
    // A flow mapping key can fold across lines like any other flow plain scalar
    // — block-context keys never reach here sitting on a newline, since a colon
    // must follow on the same line there.
    return if (inFlow && position < localLimit && isLineBreakByte(byte = localRawData[position])) {
        foldFlowPlainScalarContinuation(firstLine = firstLine) ?: firstLine
    } else {
        firstLine
    }
}

/**
 * Reads a quoted key via [readQuoted], then — in block context only — rejects it if it
 * spanned more than one line. An implicit block-mapping key (no `?` indicator) must fit on a
 * single line, unlike an ordinary quoted-scalar value (which folds fine); a flow-mapping key
 * has no such restriction since its brackets give an unambiguous boundary (cases 9BXH/9SA2
 * expect a folded multi-line flow key to succeed, JKF3 expects the block equivalent to fail).
 */
private inline fun GhostYamlFlatReader.readQuotedKeyRejectingMultiLine(
    inFlow: Boolean,
    readQuoted: () -> String
): String {
    val startPosition = position
    val text = readQuoted()
    if (inFlow) return text
    var scanPosition = startPosition
    while (scanPosition < position) {
        if (isLineBreakByte(byte = rawData[scanPosition])) {
            yamlError(message = EM.ERR_IMPLICIT_KEY_MULTILINE)
        }
        scanPosition++
    }
    return text
}
