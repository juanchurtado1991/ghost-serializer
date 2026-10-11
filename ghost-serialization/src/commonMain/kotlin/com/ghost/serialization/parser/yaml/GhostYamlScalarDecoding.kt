package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Resolves raw scalar bytes to the right Kotlin type (YAML 1.2 core schema priority:
 * null → bool → int → float → string), never allocating a String until needed. Pulled out of
 * `GhostYamlFlatReader` since this layer is only called *from* the core block/flow parser and
 * never calls back into it. Quoted-string unescaping and number parsing live alongside this in
 * `GhostYamlQuotedScalarDecoding.kt`/`GhostYamlNumberDecoding.kt`, since [interpretScalar] is
 * their only caller outside those files. A scalar that does not match its explicit `!!int`,
 * `!!float` or `!!bool` tag is an error, not a silent `0`/`false`.
 */
internal fun GhostYamlFlatReader.interpretScalar(data: ByteArray, start: Int, end: Int, expectedTag: Int): Any? {
    val length = end - start
    // A tag forcing string type still resolves to "" on empty content (e.g. "!!str,") — same
    // as at EOF in readValue — so this runs before the untagged "empty means null" default below.
    if (expectedTag == GhostYamlTags.TAG_STR) {
        return data.decodeToString(start, end)
    }
    if (length == 0) return null

    val firstByte = data[start]

    if (expectedTag == GhostYamlTags.TAG_NULL) {
        return null
    }
    if (expectedTag == GhostYamlTags.TAG_BOOL) {
        if (isTrueLiteral(data = data, start = start, length = length)) return true
        if (isFalseLiteral(data = data, start = start, length = length)) return false
        yamlError("${EM.ERR_EXPECTED_BOOLEAN_PREFIX}${data.decodeToString(start, end)}")
    }
    if (expectedTag == GhostYamlTags.TAG_INT) {
        val number = tryParseNumber(data = data, start = start, end = end) ?: data.decodeToString(start, end)
        return wholeNumberOrNull(value = number)
            ?: yamlError("${EM.ERR_EXPECTED_INT_PREFIX}${data.decodeToString(start, end)}")
    }
    if (expectedTag == GhostYamlTags.TAG_FLOAT) {
        val number = tryParseNumber(data = data, start = start, end = end)
        return (number as? Number)?.toDouble()
            ?: data.decodeToString(start, end).toDoubleOrNull()
            ?: yamlError("${EM.ERR_EXPECTED_DOUBLE_PREFIX}${data.decodeToString(start, end)}")
    }

    // null: ~, null, Null, NULL
    if (firstByte == TOK.TILDE_BYTE && length == 1) return null
    if (isNullLiteral(data = data, start = start, length = length)) return null

    // bool: true, True, TRUE, false, False, FALSE
    if (isTrueLiteral(data = data, start = start, length = length)) return true
    if (isFalseLiteral(data = data, start = start, length = length)) return false

    // number: starts with digit, '-', or '.' (for .inf/.nan)
    val isNumberLeadByte = firstByte == TOK.DASH_BYTE || isDigit(firstByte) || firstByte == TOK.DOT_BYTE
    if (isNumberLeadByte) {
        tryParseNumber(data = data, start = start, end = end)?.let { return it }
    }

    // Fallback: string
    return data.decodeToString(start, end)
}

/** Checks if bytes[start..start+len) match 'null', 'Null', or 'NULL'. */
private fun isNullLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 4) return false
    val byte0 = (data[start].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == TOK.LOWERCASE_N_BYTE && byte1 == TOK.LOWERCASE_U_BYTE &&
        byte2 == TOK.LOWERCASE_L_BYTE && byte3 == TOK.LOWERCASE_L_BYTE
}

/** Checks if bytes[start..start+len) match 'true', 'True', or 'TRUE'. */
private fun isTrueLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 4) return false
    val byte0 = (data[start].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == TOK.LOWERCASE_T_BYTE && byte1 == TOK.LOWERCASE_R_BYTE &&
        byte2 == TOK.LOWERCASE_U_BYTE && byte3 == TOK.LOWERCASE_E_BYTE
}

/** Checks if bytes[start..start+len) match 'false', 'False', or 'FALSE'. */
private fun isFalseLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 5) return false
    val byte0 = (data[start].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    val byte4 = (data[start + 4].toInt() or SC.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == TOK.LOWERCASE_F_BYTE && byte1 == TOK.LOWERCASE_A_BYTE && byte2 == TOK.LOWERCASE_L_BYTE &&
        byte3 == TOK.LOWERCASE_S_BYTE && byte4 == TOK.LOWERCASE_E_BYTE
}
