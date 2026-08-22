package com.ghost.serialization.yaml.exception

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.yaml.GhostYamlConstants as Y

/**
 * Exception thrown when Ghost encounters invalid or unsupported YAML content.
 *
 * Cursor-phase errors (typed deserialize walking the AST) include a JSONPath-style [path]
 * (e.g. `$.user.age`) and an optional [hint]. Byte-parse errors keep [path] as `"$"` — the
 * document is not yet a navigable AST, so inventing a deeper path would be misleading.
 */
class GhostYamlException(
    private val baseMessage: String,
    /**
     * JSONPath-style location for cursor-phase failures (e.g. `$.addresses[1].zip`).
     * Defaults to `"$"` for parse-phase errors and unknown locations.
     */
    val path: String = "$",
    /**
     * Optional fix suggestion. Omitted when there is no clear remediation.
     */
    val hint: String? = null,
) : RuntimeException() {

    override val message: String
        get() {
            val location = "[path $path]"
            return if (hint.isNullOrEmpty()) {
                "$baseMessage $location"
            } else {
                "$baseMessage $location\nHint: $hint"
            }
        }
}

/**
 * Maps well-known YAML / shared decode error prefixes to short fix suggestions.
 * Prefers [hintForJsonError] for shared messages (required field, discriminator, enum, …),
 * then adds only YAML-specific remediations that are clearly actionable.
 */
@InternalGhostApi
internal fun hintForYamlError(message: String): String? {
    hintForJsonError(message)?.let { return it }

    return when {
        message.startsWith(Y.ERR_EXPECTED_MAP_PREFIX) ->
            "Expected a YAML mapping here — check the value type at this path."

        message.startsWith(Y.ERR_EXPECTED_LIST_PREFIX) ->
            "Expected a YAML sequence/list here — check the value type at this path."

        message.startsWith(Y.ERR_EXPECTED_INT_PREFIX) ||
            message.startsWith(Y.ERR_EXPECTED_LONG_PREFIX) ||
            message.startsWith(Y.ERR_EXPECTED_DOUBLE_PREFIX) ||
            message.startsWith(Y.ERR_EXPECTED_FLOAT_PREFIX) ||
            message.startsWith(Y.ERR_EXPECTED_ULONG_PREFIX) ->
            "Check the scalar type at this path. For numeric strings, enable coerceStringsToNumbers."

        message.startsWith(Y.ERR_EXPECTED_BOOLEAN_PREFIX) ->
            "If the API sends string booleans, enable coerceBooleans on the reader options."

        message.startsWith(Y.ERR_MAX_NESTING_DEPTH_PREFIX) ->
            "Reduce nesting, or raise maxDepth on the YAML reader if this document is intentionally deep."

        message.startsWith(Y.ERR_ANCHOR_NOT_FOUND_PREFIX) ->
            "Define the anchor with &name before referencing it with *name in this document."

        else -> null
    }
}
