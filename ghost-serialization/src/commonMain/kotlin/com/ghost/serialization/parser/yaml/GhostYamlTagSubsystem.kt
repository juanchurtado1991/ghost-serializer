package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

internal object GhostYamlTags {
    const val TAG_NONE = 0
    const val TAG_STR = 1
    const val TAG_INT = 2
    const val TAG_FLOAT = 3
    const val TAG_BOOL = 4
    const val TAG_NULL = 5
    const val TAG_SEQ = 6
    const val TAG_MAP = 7
}

internal fun GhostYamlFlatReader.readTaggedValue(indent: Int, inFlow: Boolean): Any? {
    position++ // consume '!'
    val localRawData = rawData
    val localLimit = limit
    if (position >= localLimit) yamlError(C.ERR_EOF_AFTER_TAG)

    var isDoubleExcl = false
    if (localRawData[position] == C.EXCLAMATION_BYTE) {
        isDoubleExcl = true
        position++
    }

    var resolvedTag: String? = null
    var tagType = GhostYamlTags.TAG_NONE

    if (isDoubleExcl) {
        val tagStart = position
        while (position < localLimit && !isTagNameTerminator(localRawData[position])) {
            position++
        }
        val tagLen = position - tagStart
        // A %TAG directive redefining the secondary handle ("!!") overrides the core schema —
        // "!!int" under a redefined "!!" is that app's custom "int" tag, not YAML's actual
        // integer type, so it must not be resolved (or type-coerced) as one.
        val customSecondaryPrefix = tagDirectives[C.STR_EXCLAMATION + C.STR_EXCLAMATION]
        if (customSecondaryPrefix != null) {
            if (tagLen > 0) resolvedTag = customSecondaryPrefix + localRawData.decodeToString(tagStart, tagStart + tagLen)
        } else if (tagLen > 0) {
            tagType = matchDoubleExclamationTag(localRawData, tagStart, tagLen)
        }
        requireValidTagTerminator(inFlow)
    } else {
        // Custom tag
        if (position < localLimit && localRawData[position] == C.LT_BYTE) {
            // Verbose tag like !<Circle>
            position++ // consume '<'
            val tagStart = position
            while (position < localLimit && localRawData[position] != C.GT_BYTE) {
                position++
            }
            val tagLen = position - tagStart
            resolvedTag = localRawData.decodeToString(tagStart, tagStart + tagLen)
            if (position < localLimit && localRawData[position] == C.GT_BYTE) {
                position++ // consume '>'
            }
        } else {
            // Short tag like !Circle or !m!Circle
            val tagStart = position
            while (position < localLimit && !isTagNameTerminator(localRawData[position])) {
                position++
            }
            requireValidTagTerminator(inFlow)
            val tagLen = position - tagStart
            if (tagLen > 0) {
                val rawTagName = localRawData.decodeToString(tagStart, tagStart + tagLen)
                // Check for namespace prefix mapping (%TAG directive)
                val exclamationIdx = rawTagName.indexOf('!')
                if (exclamationIdx != -1) {
                    val handle = C.STR_EXCLAMATION + rawTagName.substring(0, exclamationIdx + 1)
                    val suffix = rawTagName.substring(exclamationIdx + 1)
                    val prefix = tagDirectives[handle]
                        ?: yamlError("${C.ERR_TAG_HANDLE_UNDEFINED_PREFIX}$handle${C.ERR_TAG_HANDLE_UNDEFINED_SUFFIX}")
                    resolvedTag = prefix + suffix
                } else {
                    resolvedTag = rawTagName
                }
            } else {
                // Bare "!" with nothing else on this token — YAML's "non-specific tag", which
                // forces the scalar to resolve as a string rather than running the usual
                // null/bool/int/float cascade (e.g. "! 12" must decode to the string "12", not
                // the integer 12).
                tagType = GhostYamlTags.TAG_STR
            }
        }
    }

    // Skip inline space after tag
    skipInlineWhitespace()

    // If value is on next line, advance and use next line's indentation
    val valueIndent =
        if (position < localLimit && (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)) {
            advanceLine()
            skipWhitespaceAndComments()
            currentIndent
        } else {
            indent
        }

    val value = when (tagType) {
        GhostYamlTags.TAG_SEQ -> {
            if (position < localLimit && localRawData[position] == C.LEFT_BRACKET_BYTE) {
                readFlowSequence()
            } else {
                skipWhitespaceAndComments()
                readBlockSequence(currentIndent)
            }
        }

        GhostYamlTags.TAG_MAP -> {
            if (position < localLimit && localRawData[position] == C.LEFT_BRACE_BYTE) {
                readFlowMapping()
            } else {
                skipWhitespaceAndComments()
                readBlockMapping(currentIndent)
            }
        }

        else -> {
            readValue(valueIndent, inFlow = inFlow, expectedTag = tagType)
        }
    }

    // Inject tag into the Map if it's a custom tag and value is a Map
    if (resolvedTag != null && value is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val map = value as MutableMap<String, Any?>
        map[C.STR_TAG_KEY] = resolvedTag
    }

    return value
}

/** Flow indicators (`,[]{}`) end a tag name the same way whitespace does — a tag name can never
 *  contain one, in either context — but *stopping* there is only valid inside an actual flow
 *  collection (a tag-only entry like `!!str,`); in block context a tag directly touching one of
 *  these with no separating whitespace is invalid, same as any other unexpected character.
 */
private fun isTagNameTerminator(currByte: Byte): Boolean =
    currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE ||
        currByte == C.COMMA_BYTE || currByte == C.LEFT_BRACE_BYTE || currByte == C.RIGHT_BRACE_BYTE ||
        currByte == C.LEFT_BRACKET_BYTE || currByte == C.RIGHT_BRACKET_BYTE

private fun GhostYamlFlatReader.requireValidTagTerminator(inFlow: Boolean) {
    if (position >= limit) return
    val currByte = rawData[position]
    val isWhitespaceOrEol = currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE ||
        currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE
    if (isWhitespaceOrEol) return
    val isFlowIndicator = currByte == C.COMMA_BYTE || currByte == C.LEFT_BRACE_BYTE ||
        currByte == C.RIGHT_BRACE_BYTE || currByte == C.LEFT_BRACKET_BYTE || currByte == C.RIGHT_BRACKET_BYTE
    if (inFlow && isFlowIndicator) return
    yamlError(C.ERR_INVALID_CHAR_AFTER_TAG)
}

private fun GhostYamlFlatReader.matchDoubleExclamationTag(
    localRawData: ByteArray,
    start: Int,
    len: Int
): Int {
    if (len == 3) {
        if (localRawData[start] == C.CHAR_S_BYTE && localRawData[start + 1] == C.CHAR_T_BYTE && localRawData[start + 2] == C.CHAR_R_BYTE) {
            return GhostYamlTags.TAG_STR
        }
        if (localRawData[start] == C.CHAR_I_BYTE && localRawData[start + 1] == C.CHAR_N_BYTE && localRawData[start + 2] == C.CHAR_T_BYTE) {
            return GhostYamlTags.TAG_INT
        }
        if (localRawData[start] == C.CHAR_S_BYTE && localRawData[start + 1] == C.CHAR_E_BYTE && localRawData[start + 2] == C.CHAR_Q_BYTE) {
            return GhostYamlTags.TAG_SEQ
        }
        if (localRawData[start] == C.CHAR_M_BYTE && localRawData[start + 1] == C.CHAR_A_BYTE && localRawData[start + 2] == C.CHAR_P_BYTE) {
            return GhostYamlTags.TAG_MAP
        }
    } else if (len == 4) {
        if (localRawData[start] == C.CHAR_B_BYTE && localRawData[start + 1] == C.CHAR_O_BYTE && localRawData[start + 2] == C.CHAR_O_BYTE && localRawData[start + 3] == C.CHAR_L_BYTE) {
            return GhostYamlTags.TAG_BOOL
        }
        if (localRawData[start] == C.CHAR_N_BYTE && localRawData[start + 1] == C.CHAR_U_BYTE && localRawData[start + 2] == C.CHAR_L_BYTE && localRawData[start + 3] == C.CHAR_L_BYTE) {
            return GhostYamlTags.TAG_NULL
        }
    } else if (len == 5) {
        if (localRawData[start] == C.CHAR_F_BYTE && localRawData[start + 1] == C.CHAR_L_BYTE && localRawData[start + 2] == C.CHAR_O_BYTE && localRawData[start + 3] == C.CHAR_A_BYTE && localRawData[start + 4] == C.CHAR_T_BYTE) {
            return GhostYamlTags.TAG_FLOAT
        }
    }
    return GhostYamlTags.TAG_NONE
}
