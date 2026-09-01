package com.ghost.serialization.exception

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Exception for JSON parsing/encoding errors.
 *
 * [line]/[column] are computed lazily: the parser can raise this in tight probing
 * loops, so the O(N) source scan only runs if a caller actually reads them.
 */
class GhostJsonException @InternalGhostApi internal constructor(
    private val baseMessage: String,
    private val computeLineCol: () -> IntArray,
    /** JSONPath of the error (e.g. `$.user.addresses[1].zip`); `"$"` if root/unknown. */
    val path: String = "$",
    /** Optional developer-facing fix suggestion. */
    val hint: String? = null,
) : RuntimeException() {

    private val lineCol: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        computeLineCol()
    }

    /** The 1-indexed line number in the JSON source where the error occurred. */
    val line: Int get() = lineCol[0]

    /** The 1-indexed column number in the JSON source where the error occurred. */
    val column: Int get() = lineCol[1]

    override val message: String
        get() {
            val location = "[at line $line, col $column, path $path]"
            return if (hint.isNullOrEmpty()) {
                "$baseMessage $location"
            } else {
                "$baseMessage $location\nHint: $hint"
            }
        }

    /** Constructs with an explicit line, column, path, and optional hint. */
    @OptIn(InternalGhostApi::class)
    constructor(
        message: String,
        line: Int = -1,
        column: Int = -1,
        path: String = "$",
        hint: String? = null,
    ) : this(
        message,
        { intArrayOf(line, column) },
        path,
        hint,
    )
}

/**
 * Maps well-known parser error prefixes to short fix suggestions.
 * Returns null when no actionable hint is known (keeps noise low).
 */
@InternalGhostApi
internal fun hintForJsonError(message: String): String? = when {
    message.startsWith(C.STRICT_MODE_UNKNOWN_FIELD) ->
        "Turn off strictMode, or add the field to the @GhostSerialization model " +
            "(wire name via @SerialName / @GhostName)."

    message.startsWith(C.ERR_COERCION_DISABLED) ->
        "Enable coerceStringsToNumbers: Ghost.deserialize(…) { it.coerceStringsToNumbers = true }."

    message.startsWith(C.ERR_EXPECTED_BOOLEAN) ->
        "If the API sends 0/1 or quoted booleans, enable coerceBooleans on the reader options."

    message.startsWith(C.ERR_TRAILING_COMMA) ||
        message.startsWith(C.ERR_UNEXPECTED_COMMA) ->
        "Remove the extra comma, or keep strictMode=false only if you intentionally accept lenient JSON."

    message.startsWith(C.ERR_NON_FINITE) ->
        "JSON cannot encode NaN/Infinity — send null or a string sentinel and map it in a @GhostDecoder."

    message.startsWith(C.ERR_LEADING_ZEROS) ->
        "JSON numbers cannot have leading zeros (e.g. 01). Send an unpadded number or a quoted string."

    message.startsWith(C.ERR_DEPTH_EXCEEDED) ->
        "Reduce nesting, or raise maxDepth on the reader if this payload is intentionally deep."

    message.startsWith(C.ERR_MAX_COLLECTION_SIZE) ->
        "Raise ghost.maxCollectionSize / GhostHeuristics.maxCollectionSize if the list is legitimate."

    message.startsWith(C.UNTERMINATED_STRING_ERROR) ||
        message.startsWith(C.UNTERMINATED_ESCAPE_ERROR) ||
        message.startsWith(C.UNTERMINATED_UNICODE_ERROR) ->
        "Check for a missing closing quote or a truncated escape (\\uXXXX) at this path."

    message.startsWith(C.ERR_EXPECTED_BEGIN_OBJ) ->
        "Expected a JSON object `{…}` here — check the value type at this path."

    message.startsWith(C.ERR_EXPECTED_BEGIN_ARR) ->
        "Expected a JSON array `[…]` here — check the value type at this path."

    message.startsWith(C.ERR_EXPECTED_STRING) ||
        message.startsWith(C.ERR_EXPECTED_KEY) ->
        "Expected a quoted string/key — check for a missing `\"` or a wrong value type at this path."

    message.startsWith(C.ERR_EXPECTED_NUMBER) ||
        message.startsWith(C.ERR_EXPECTED_INT_PART) ||
        message.startsWith(C.ERR_INT_OVERFLOW) ||
        message.startsWith(C.ERR_LONG_OVERFLOW) ->
        "Check the JSON type at this path (number vs string/object/bool). " +
            "For numeric strings, enable coerceStringsToNumbers."

    message.startsWith(C.ERR_REQUIRED_FIELD_PREFIX) ->
        "Add the field to the JSON, make the property nullable/defaulted, or check the wire name " +
            "(@SerialName / @GhostName)."

    message.startsWith(C.ERR_MISSING_DISCRIMINATOR) ->
        "Include the sealed-class discriminator key in the JSON object " +
            "(default `type`, or the key from @GhostDiscriminator)."

    message.startsWith(C.ERR_UNKNOWN_DISCRIMINATOR_PREFIX) ->
        "Add a matching @GhostSerialization subclass, or annotate one with @GhostFallback " +
            "to absorb unknown variants."

    message.startsWith(C.ERR_INVALID_ENUM_VALUE) ||
        message.startsWith(C.ERR_UNEXPECTED_ENUM_INDEX_PREFIX) ->
        "Fix the wire value, add an enum entry, or use @GhostFallback / an UNKNOWN entry " +
            "(or @GhostResilient on the property)."

    message.startsWith(C.ERR_UNKNOWN_ENUM) ->
        "Map the enum wire value, or provide a fallback / UNKNOWN constant for proto enums."

    message.startsWith(C.ERR_INVALID_BASE64) ->
        "Proto bytes fields expect standard base64 (or base64url) without invalid characters."

    message.startsWith(C.ERR_PROTO_UINT32_OVERFLOW) ||
        message.startsWith(C.ERR_PROTO_FRACTIONAL_INT) ->
        "Proto integer fields reject fractions and values outside the target wire range."

    else -> null
}
