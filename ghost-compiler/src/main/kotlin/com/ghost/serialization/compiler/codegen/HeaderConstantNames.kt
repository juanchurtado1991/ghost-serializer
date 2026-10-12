package com.ghost.serialization.compiler.codegen

import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC

/**
 * Suffixes of the cached `H_*`/`HS_*` header constants for a class's JSON field names, computed by
 * [allocate] from the class's whole name set by both the declaration and every write site, so they
 * always agree. A name made only of ASCII letters, digits, `_` and `.` keeps its historical form
 * (`requestId` → `REQUESTID`); any other character (`renamed-key`, spaces, non-ASCII) is replaced
 * with `_`. When two names of a class map to the same suffix (`id`/`ID`, `a.b`/`a_b`, or a replaced
 * character), the later one in sorted order gets its hash appended.
 */
internal object HeaderConstantNames {

    private const val ASCII_LIMIT = 128
    private const val HASH_RADIX = 16

    fun allocate(
        jsonNames: Collection<String>
    ): Map<String, String> {
        val taken = mutableSetOf<String>()
        val suffixes = LinkedHashMap<String, String>()
        for (name in jsonNames.distinct().sorted()) {
            var suffix = suffixFor(jsonName = name)
            if (!taken.add(suffix)) {
                suffix += CC.STR_UNDERSCORE + hashSuffix(jsonName = name)
                while (!taken.add(suffix)) {
                    suffix += CC.STR_UNDERSCORE
                }
            }
            suffixes[name] = suffix
        }
        return suffixes
    }

    fun suffixFor(
        jsonName: String
    ): String {
        val historical = jsonName.replace(CC.STR_DOT, CC.STR_UNDERSCORE).uppercase()
        if (jsonName.all { isPlainNameChar(char = it) }) {
            return historical
        }
        val sanitized = historical
            .map { char -> if (isPlainNameChar(char = char)) char else CC.STR_UNDERSCORE.single() }
            .joinToString(separator = CC.STR_EMPTY)
        return sanitized + CC.STR_UNDERSCORE + hashSuffix(jsonName = jsonName)
    }

    private fun hashSuffix(
        jsonName: String
    ): String = jsonName.hashCode().toUInt().toString(radix = HASH_RADIX).uppercase()

    private fun isPlainNameChar(
        char: Char
    ): Boolean {
        val isAsciiLetterOrDigit = char.code < ASCII_LIMIT && char.isLetterOrDigit()
        return isAsciiLetterOrDigit || char == CC.STR_UNDERSCORE.single() || char == CC.STR_DOT.single()
    }
}
