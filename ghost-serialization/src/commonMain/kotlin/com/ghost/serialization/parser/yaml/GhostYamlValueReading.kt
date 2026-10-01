@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Reads the next YAML value at the current position.
 *
 * @param indent The indentation level of the enclosing context (INDENT_UNSET for root).
 * @param inFlow Whether we are inside a flow collection `{...}` or `[...]`.
 */
internal fun GhostYamlFlatReader.readValue(
    indent: Int,
    inFlow: Boolean,
    expectedTag: Int = GhostYamlTags.TAG_NONE,
    strictDedent: Boolean = false,
    allowMappingRedirect: Boolean = true,
    // Distinct from `indent`: if this value is a plain scalar, later lines must indent more
    // than *this* to keep folding into it. Normally equals `indent`, but diverges for a
    // mapping value starting on its own line: `indent` there is this value's auto-detected
    // column (needed if it turns out to be a nested block collection), while a plain
    // scalar's fold boundary per spec is the *enclosing mapping's* indent, not the value's.
    foldIndent: Int = indent
): Any? {
    skipInlineWhitespace()
    val localLimit = limit
    if (position >= localLimit) {
        // A bare "!!str" with nothing after it, even at EOF, still resolves to an empty
        // string, not "no value at all" — same as trailing whitespace/newline instead of EOF.
        return if (expectedTag == GhostYamlTags.TAG_STR) "" else null
    }

    return when (val currentByte = rawData[position]) {
        TOK.PIPE_BYTE, TOK.GT_BYTE -> readBlockScalar(indicator = currentByte, indent = indent)
        TOK.LEFT_BRACE_BYTE -> readFlowCollectionOrMappingKey(indent = indent, inFlow = inFlow) { readFlowMapping() }
        TOK.LEFT_BRACKET_BYTE -> readFlowCollectionOrMappingKey(indent = indent, inFlow = inFlow) { readFlowSequence() }
        TOK.EXCLAMATION_BYTE -> readTaggedValue(indent = indent, inFlow = inFlow)
        TOK.AMPERSAND_BYTE -> readAnchoredValueOrMappingKey(
            indent = indent,
            inFlow = inFlow,
            strictDedent = strictDedent
        )
        TOK.ASTERISK_BYTE -> readAliasOrMappingKey(indent = indent, inFlow = inFlow)
        TOK.DOUBLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(
            indent = indent,
            inFlow = inFlow
        ) { readDoubleQuotedString() }
        TOK.SINGLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(
            indent = indent,
            inFlow = inFlow
        ) { readSingleQuotedString() }
        TOK.DOT_BYTE -> if (isDocumentEndMarker()) {
            null
        } else {
            readPlainScalarOrMapping(
                indent = indent,
                inFlow = inFlow,
                expectedTag = expectedTag,
                allowMappingRedirect = allowMappingRedirect,
                foldIndent = foldIndent
            )
        }
        // '%' is reserved for directives, never a plain scalar start (no "followed by a safe
        // character" exception like '-'/'?'/':' get) — a directive-shaped line where a value
        // is expected is invalid, not a scalar starting with '%'.
        TOK.PERCENT_BYTE -> yamlError(message = EM.ERR_PLAIN_SCALAR_PERCENT)
        TOK.QUESTION_BYTE ->
            if (!inFlow && isExplicitKeyIndicator()) readBlockMapping(blockIndent = indent.coerceAtLeast(0))
            else readPlainScalarOrMapping(
                indent = indent,
                inFlow = inFlow,
                expectedTag = expectedTag,
                allowMappingRedirect = allowMappingRedirect,
                foldIndent = foldIndent
            )
        TOK.DASH_BYTE -> {
            // Negative number "-42", block sequence "- item", or doc separator "---".
            val nextByte = if (position + 1 < localLimit) rawData[position + 1] else 0
            when {
                expectedTag != GhostYamlTags.TAG_STR && isDigit(nextByte) -> readNumber()
                nextByte == TOK.SPACE_BYTE || isLineBreakByte(byte = nextByte) ||
                    nextByte == TOK.TAB_BYTE || position + 1 >= localLimit ->
                    readBlockSequence(seqIndent = indent)

                isDocumentMarker() -> null
                else -> readPlainScalar(
                    indent = indent,
                    inFlow = inFlow,
                    expectedTag = expectedTag,
                    allowMappingRedirect = allowMappingRedirect
                )
            }
        }

        else -> readPlainScalarOrMapping(
            indent = indent,
            inFlow = inFlow,
            expectedTag = expectedTag,
            allowMappingRedirect = allowMappingRedirect,
            foldIndent = foldIndent
        )
    }
}

/**
 * Reads a quoted scalar, then checks whether a `:` follows — a quoted string can be a
 * mapping key too (e.g. `"400":`), a case [readPlainScalarOrMapping]'s colon-scan already
 * handles for bare keys but readValue's dispatch never reaches for quoted content.
 */
private inline fun GhostYamlFlatReader.readQuotedScalarOrMappingKey(
    indent: Int,
    inFlow: Boolean,
    readQuoted: () -> String
): Any? {
    val startPosition = position
    val text = readQuoted()
    skipInlineWhitespace()
    val localLimit = limit
    val isMappingKey = isMappingColonAt(rawData = rawData, position = position, limit = localLimit)
    // Inside a flow collection there's no block mapping to redirect into — leave position
    // where it is and let the caller (a flow sequence entry may be an implicit single-pair
    // mapping) decide.
    if (inFlow || !isMappingKey) return text
    position = startPosition
    return readBlockMapping(blockIndent = indent.coerceAtLeast(0))
}

/**
 * Like [readQuotedScalarOrMappingKey] but for flow collections: a flow collection can itself
 * be a block-mapping key (e.g. `[flow]: block`), not just a value.
 */
private inline fun GhostYamlFlatReader.readFlowCollectionOrMappingKey(
    indent: Int,
    inFlow: Boolean,
    readCollection: () -> Any?
): Any? {
    val startPosition = position
    val collection = readCollection()
    if (inFlow) return collection
    skipInlineWhitespace()
    val localLimit = limit
    val isMappingKey = isMappingColonAt(rawData = rawData, position = position, limit = localLimit)
    if (!isMappingKey) return collection
    position = startPosition
    return readBlockMapping(blockIndent = indent.coerceAtLeast(0))
}

/**
 * True if [rawData] has a `:` at [position] immediately followed by whitespace, a line break,
 * or EOF. Shared by value/key dispatch across [GhostYamlValueReading.kt], the plain-scalar
 * folders in `GhostYamlPlainScalarFolding.kt`, and `GhostYamlKeyReading.kt`.
 */
internal inline fun isMappingColonAt(rawData: ByteArray, position: Int, limit: Int): Boolean {
    if (position >= limit || rawData[position] != TOK.COLON_BYTE) return false
    val next = position + 1
    return next >= limit ||
        rawData[next] == TOK.SPACE_BYTE ||
        rawData[next] == TOK.NEWLINE_BYTE ||
        rawData[next] == TOK.CR_BYTE ||
        rawData[next] == TOK.TAB_BYTE
}

/**
 * True if [byte] ends a flow scalar (a comma or the closing bracket/brace of the enclosing
 * collection). Shared with the plain-scalar folders and key reading, same reason as
 * [isMappingColonAt].
 */
internal fun isFlowScalarTerminatorByte(byte: Byte): Boolean =
    byte == TOK.COMMA_BYTE || byte == TOK.RIGHT_BRACE_BYTE || byte == TOK.RIGHT_BRACKET_BYTE
