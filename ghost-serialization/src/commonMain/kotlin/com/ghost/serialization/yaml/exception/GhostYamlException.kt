package com.ghost.serialization.yaml.exception

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_ANCHOR_NOT_FOUND
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_COERCE_BOOLEANS
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_EXPECTED_LIST
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_EXPECTED_MAP
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_EXPECTED_SCALAR
import com.ghost.serialization.yaml.exception.GhostYamlHintMessages.HINT_MAX_NESTING_DEPTH

/**
 * Exception thrown when Ghost encounters invalid or unsupported YAML content.
 *
 * Cursor-phase errors (typed deserialize walking the AST) include a JSONPath-style [path]
 * (e.g. `$.user.age`). Byte-parse errors keep [path] as `"$"` — the document is not yet a
 * navigable AST, so inventing a deeper path would be misleading.
 * @param path JSONPath-style location for cursor-phase failures; `"$"` for parse-phase/unknown.
 * @param hint Optional developer-facing fix suggestion.
 */
class GhostYamlException(
    private val baseMessage: String,
    val path: String = ROOT_PATH,
    val hint: String? = null,
) : RuntimeException() {

    override val message: String
        get() = "$baseMessage [path $path]" +
            if (hint.isNullOrEmpty()) "" else "$HINT_PREFIX$hint"

    companion object {
        private const val HINT_PREFIX = "\nHint: "
        private const val ROOT_PATH = "$"
    }
}

/**
 * Maps well-known YAML / shared decode error prefixes to short fix suggestions.
 * Prefers [hintForJsonError] for shared messages (required field, discriminator, enum, …),
 * then adds only YAML-specific remediation's that are clearly actionable.
 */
@InternalGhostApi
internal fun String.hintForYamlError(): String? {
    hintForJsonError()?.let { return it }

    return when {
        startsWith(prefix = EM.ERR_EXPECTED_MAP_PREFIX) -> HINT_EXPECTED_MAP

        startsWith(prefix = EM.ERR_EXPECTED_LIST_PREFIX) -> HINT_EXPECTED_LIST

        startsWith(prefix = EM.ERR_EXPECTED_INT_PREFIX) ||
            startsWith(prefix = EM.ERR_EXPECTED_LONG_PREFIX) ||
            startsWith(prefix = EM.ERR_EXPECTED_DOUBLE_PREFIX) ||
            startsWith(prefix = EM.ERR_EXPECTED_FLOAT_PREFIX) ||
            startsWith(prefix = EM.ERR_EXPECTED_ULONG_PREFIX) -> HINT_EXPECTED_SCALAR

        startsWith(prefix = EM.ERR_EXPECTED_BOOLEAN_PREFIX) -> HINT_COERCE_BOOLEANS

        startsWith(prefix = EM.ERR_MAX_NESTING_DEPTH_PREFIX) -> HINT_MAX_NESTING_DEPTH

        startsWith(prefix = EM.ERR_ANCHOR_NOT_FOUND_PREFIX) -> HINT_ANCHOR_NOT_FOUND

        else -> null
    }
}
