package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Parses a `!tag` node: `!!name` (core-schema, or app-defined if `!!` was redefined by a `%TAG`
 * directive), `!<verbose>`, `!name`/`!handle!name` (namespaced via a `%TAG` directive), or a bare
 * `!` (YAML's non-specific tag, forcing string resolution). The resolved tag type then decides
 * whether the value itself is read as a sequence, mapping, or plain [readValue] dispatch.
 */
internal fun GhostYamlFlatReader.readTaggedValue(indent: Int, inFlow: Boolean): Any? {
    position++ // consume '!'
    if (position >= limit) yamlError(message = EM.ERR_EOF_AFTER_TAG)

    val isDoubleExcl = rawData[position] == TOK.EXCLAMATION_BYTE
    if (isDoubleExcl) position++

    val (resolvedTag, tagType) = if (isDoubleExcl) {
        resolveDoubleExclamationTag(inFlow = inFlow)
    } else {
        resolveCustomTag(inFlow = inFlow)
    }

    skipInlineWhitespace()
    val valueIndent = resolveTaggedValueIndent(indent = indent)
    val value = readTaggedCollectionOrValue(tagType = tagType, valueIndent = valueIndent, inFlow = inFlow)
    attachResolvedTag(value = value, resolvedTag = resolvedTag)
    return value
}

/** Resolves `!!name`: either a core-schema type (`tagType`) or an app-redefined `!!` (`resolvedTag`). */
private fun GhostYamlFlatReader.resolveDoubleExclamationTag(inFlow: Boolean): Pair<String?, Int> {
    val localRawData = rawData
    val localLimit = limit
    val tagStart = position
    while (position < localLimit && !isTagNameTerminator(currByte = localRawData[position])) {
        position++
    }
    val tagLen = position - tagStart
    // A %TAG directive redefining the secondary handle ("!!") overrides the core schema —
    // "!!int" under a redefined "!!" is the app's custom "int" tag, not YAML's integer
    // type, so it must not be resolved (or type-coerced) as one.
    val customSecondaryPrefix = tagDirectives[TOK.STR_EXCLAMATION + TOK.STR_EXCLAMATION]
    var resolvedTag: String? = null
    var tagType = GhostYamlTags.TAG_NONE
    if (customSecondaryPrefix != null) {
        if (tagLen > 0) {
            resolvedTag = customSecondaryPrefix + localRawData.decodeToString(tagStart, tagStart + tagLen)
        }
    } else if (tagLen > 0) {
        tagType = matchDoubleExclamationTag(localRawData = localRawData, start = tagStart, len = tagLen)
    }
    requireValidTagTerminator(inFlow = inFlow)
    return resolvedTag to tagType
}

/** Resolves a verbose (`!<Circle>`) or short (`!Circle`/`!m!Circle`) custom tag. */
private fun GhostYamlFlatReader.resolveCustomTag(inFlow: Boolean): Pair<String?, Int> {
    if (position < limit && rawData[position] == TOK.LT_BYTE) {
        return resolveVerboseTag() to GhostYamlTags.TAG_NONE
    }
    return resolveShortTag(inFlow = inFlow)
}

/** Reads a verbose tag like `!<Circle>`, consuming its `<`/`>` delimiters. */
private fun GhostYamlFlatReader.resolveVerboseTag(): String {
    val localRawData = rawData
    val localLimit = limit
    position++ // consume '<'
    val tagStart = position
    while (position < localLimit && localRawData[position] != TOK.GT_BYTE) {
        position++
    }
    val resolvedTag = localRawData.decodeToString(tagStart, position)
    if (position < localLimit && localRawData[position] == TOK.GT_BYTE) {
        position++ // consume '>'
    }
    return resolvedTag
}

/** Reads a short tag like `!Circle` or `!m!Circle`, resolving any `%TAG` namespace prefix. */
private fun GhostYamlFlatReader.resolveShortTag(inFlow: Boolean): Pair<String?, Int> {
    val localRawData = rawData
    val localLimit = limit
    val tagStart = position
    while (position < localLimit && !isTagNameTerminator(currByte = localRawData[position])) {
        position++
    }
    requireValidTagTerminator(inFlow = inFlow)
    val tagLen = position - tagStart
    if (tagLen == 0) {
        // Bare "!" with nothing else — YAML's "non-specific tag", forcing the scalar to
        // resolve as a string instead of running the usual null/bool/int/float cascade
        // (e.g. "! 12" must decode to string "12", not integer 12).
        return null to GhostYamlTags.TAG_STR
    }
    val rawTagName = localRawData.decodeToString(tagStart, tagStart + tagLen)
    return resolveTagNamespacePrefix(rawTagName = rawTagName) to GhostYamlTags.TAG_NONE
}

/** Expands a short tag's `!handle!` namespace prefix via its `%TAG` directive, if it has one. */
private fun GhostYamlFlatReader.resolveTagNamespacePrefix(rawTagName: String): String {
    val exclamationIdx = rawTagName.indexOf('!')
    if (exclamationIdx == -1) return rawTagName
    val handle = TOK.STR_EXCLAMATION + rawTagName.substring(0, exclamationIdx + 1)
    val suffix = rawTagName.substring(exclamationIdx + 1)
    val prefix = tagDirectives[handle]
        ?: yamlError(message = "${EM.ERR_TAG_HANDLE_UNDEFINED_PREFIX}$handle${EM.ERR_TAG_HANDLE_UNDEFINED_SUFFIX}")
    return prefix + suffix
}

/** Auto-detects the tagged value's indent when it starts on a following line. */
private fun GhostYamlFlatReader.resolveTaggedValueIndent(indent: Int): Int {
    if (position >= limit || !isLineBreakByte(byte = rawData[position])) return indent
    advanceLine()
    skipWhitespaceAndComments()
    return currentIndent
}

/** Dispatches to a sequence/mapping reader when the tag names one, else falls through to [readValue]. */
private fun GhostYamlFlatReader.readTaggedCollectionOrValue(tagType: Int, valueIndent: Int, inFlow: Boolean): Any? =
    when (tagType) {
        GhostYamlTags.TAG_SEQ -> readTaggedSequence()
        GhostYamlTags.TAG_MAP -> readTaggedMapping()
        else -> readValue(valueIndent, inFlow = inFlow, expectedTag = tagType)
    }

private fun GhostYamlFlatReader.readTaggedSequence(): List<Any?> =
    if (position < limit && rawData[position] == TOK.LEFT_BRACKET_BYTE) {
        readFlowSequence()
    } else {
        skipWhitespaceAndComments()
        readBlockSequence(seqIndent = currentIndent)
    }

private fun GhostYamlFlatReader.readTaggedMapping(): Map<String, Any?> =
    if (position < limit && rawData[position] == TOK.LEFT_BRACE_BYTE) {
        readFlowMapping()
    } else {
        skipWhitespaceAndComments()
        readBlockMapping(blockIndent = currentIndent)
    }

/** Stamps [resolvedTag] (a custom/verbose tag name) onto a mapping result, if there is one to attach. */
private fun attachResolvedTag(value: Any?, resolvedTag: String?) {
    if (resolvedTag != null && value is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val map = value as MutableMap<String, Any?>
        map[TOK.STR_TAG_KEY] = resolvedTag
    }
}

/**
 * Flow indicators (`,[]{}`) end a tag name like whitespace does — a tag name can never contain
 * one — but *stopping* there is only valid inside a flow collection (a tag-only entry like
 * `!!str,`); in block context a tag touching one with no separating whitespace is invalid.
 */
private fun isTagNameTerminator(currByte: Byte): Boolean =
    currByte == TOK.SPACE_BYTE || currByte == TOK.TAB_BYTE || currByte == TOK.NEWLINE_BYTE || currByte == TOK.CR_BYTE ||
        currByte == TOK.COMMA_BYTE || currByte == TOK.LEFT_BRACE_BYTE || currByte == TOK.RIGHT_BRACE_BYTE ||
        currByte == TOK.LEFT_BRACKET_BYTE || currByte == TOK.RIGHT_BRACKET_BYTE

private fun GhostYamlFlatReader.requireValidTagTerminator(inFlow: Boolean) {
    if (position >= limit) return
    val currByte = rawData[position]
    val isWhitespaceOrEol = currByte == TOK.SPACE_BYTE || currByte == TOK.TAB_BYTE ||
        currByte == TOK.NEWLINE_BYTE || currByte == TOK.CR_BYTE
    if (isWhitespaceOrEol) return
    val isFlowIndicator = currByte == TOK.COMMA_BYTE || currByte == TOK.LEFT_BRACE_BYTE ||
        currByte == TOK.RIGHT_BRACE_BYTE || currByte == TOK.LEFT_BRACKET_BYTE || currByte == TOK.RIGHT_BRACKET_BYTE
    if (inFlow && isFlowIndicator) return
    yamlError(message = EM.ERR_INVALID_CHAR_AFTER_TAG)
}

/** Byte-length of the 3-char core-schema tag names: `str`/`int`/`seq`/`map`. */
private const val THREE_CHAR_TAG_LEN = 3

/** Byte-length of the 4-char core-schema tag names: `bool`/`null`. */
private const val FOUR_CHAR_TAG_LEN = 4

/** Byte-length of the 5-char core-schema tag name: `float`. */
private const val FIVE_CHAR_TAG_LEN = 5

private fun GhostYamlFlatReader.matchDoubleExclamationTag(
    localRawData: ByteArray,
    start: Int,
    len: Int
): Int {
    if (len == THREE_CHAR_TAG_LEN) {
        val isStrTag = localRawData[start] == TOK.LOWERCASE_S_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_T_BYTE &&
            localRawData[start + 2] == TOK.LOWERCASE_R_BYTE
        if (isStrTag) return GhostYamlTags.TAG_STR

        val isIntTag = localRawData[start] == TOK.CHAR_I_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_N_BYTE &&
            localRawData[start + 2] == TOK.LOWERCASE_T_BYTE
        if (isIntTag) return GhostYamlTags.TAG_INT

        val isSeqTag = localRawData[start] == TOK.LOWERCASE_S_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_E_BYTE &&
            localRawData[start + 2] == TOK.CHAR_Q_BYTE
        if (isSeqTag) return GhostYamlTags.TAG_SEQ

        val isMapTag = localRawData[start] == TOK.CHAR_M_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_A_BYTE &&
            localRawData[start + 2] == TOK.CHAR_P_BYTE
        if (isMapTag) return GhostYamlTags.TAG_MAP
    } else if (len == FOUR_CHAR_TAG_LEN) {
        val isBoolTag = localRawData[start] == TOK.CHAR_B_BYTE &&
            localRawData[start + 1] == TOK.CHAR_O_BYTE &&
            localRawData[start + 2] == TOK.CHAR_O_BYTE &&
            localRawData[start + 3] == TOK.LOWERCASE_L_BYTE
        if (isBoolTag) return GhostYamlTags.TAG_BOOL

        val isNullTag = localRawData[start] == TOK.LOWERCASE_N_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_U_BYTE &&
            localRawData[start + 2] == TOK.LOWERCASE_L_BYTE &&
            localRawData[start + 3] == TOK.LOWERCASE_L_BYTE
        if (isNullTag) return GhostYamlTags.TAG_NULL
    } else if (len == FIVE_CHAR_TAG_LEN) {
        val isFloatTag = localRawData[start] == TOK.LOWERCASE_F_BYTE &&
            localRawData[start + 1] == TOK.LOWERCASE_L_BYTE &&
            localRawData[start + 2] == TOK.CHAR_O_BYTE &&
            localRawData[start + 3] == TOK.LOWERCASE_A_BYTE &&
            localRawData[start + 4] == TOK.LOWERCASE_T_BYTE
        if (isFloatTag) return GhostYamlTags.TAG_FLOAT
    }
    return GhostYamlTags.TAG_NONE
}
