package com.ghost.serialization.exception

object GhostJsonHintMessages {
    const val HINT_COERCE_BOOLEANS =
        "If the API sends 0/1 or quoted booleans, enable coerceBooleans on the reader options."
    const val HINT_COERCION_DISABLED =
        "Enable coerceStringsToNumbers: Ghost.deserialize(…) { it.coerceStringsToNumbers = true }."
    const val HINT_DEPTH_EXCEEDED =
        "Reduce nesting, or raise maxDepth on the reader if this payload is intentionally deep."
    const val HINT_EXPECTED_ARRAY =
        "Expected a JSON array `[…]` here — check the value type at this path."
    const val HINT_EXPECTED_NUMBER = "Check the JSON type at this path (number vs string/object/bool). " +
            "For numeric strings, enable coerceStringsToNumbers."
    const val HINT_EXPECTED_OBJECT =
        "Expected a JSON object `{…}` here — check the value type at this path."
    const val HINT_EXPECTED_STRING =
        "Expected a quoted string/key — check for a missing `\"` or a wrong value type at this path."
    const val HINT_INVALID_BASE64 =
        "Proto bytes fields expect standard base64 (or base64url) without invalid characters."
    const val HINT_INVALID_ENUM_VALUE = "Fix the wire value, add an enum entry, or use @GhostFallback / an UNKNOWN entry " +
            "(or @GhostResilient on the property)."
    const val HINT_LEADING_ZEROS =
        "JSON numbers cannot have leading zeros (e.g. 01). Send an unpadded number or a quoted string."
    const val HINT_MAX_COLLECTION_SIZE =
        "Raise ghost.maxCollectionSize / GhostHeuristics.maxCollectionSize if the list is legitimate."
    const val HINT_MISSING_DISCRIMINATOR = "Include the sealed-class discriminator key in the JSON object " +
            "(default `type`, or the key from @GhostDiscriminator)."
    const val HINT_NON_FINITE =
        "JSON cannot encode NaN/Infinity — send null or a string sentinel and map it in a @GhostDecoder."
    const val HINT_PROTO_INT_RANGE =
        "Proto integer fields reject fractions and values outside the target wire range."
    const val HINT_REQUIRED_FIELD = "Add the field to the JSON, make the property nullable/defaulted, or check the wire name " +
            "(@SerialName / @GhostName)."
    const val HINT_UNEXPECTED_COMMA =
        "Remove the extra comma, or keep strictMode=false only if you intentionally accept lenient JSON."
    const val HINT_UNKNOWN_DISCRIMINATOR = "Add a matching @GhostSerialization subclass, or annotate one with @GhostFallback " +
            "to absorb unknown variants."
    const val HINT_UNKNOWN_ENUM =
        "Map the enum wire value, or provide a fallback / UNKNOWN constant for proto enums."
    const val HINT_UNKNOWN_FIELD = "Turn off strictMode, or add the field to the @GhostSerialization model " +
            "(wire name via @SerialName / @GhostName)."
    const val HINT_UNTERMINATED =
        "Check for a missing closing quote or a truncated escape (\\uXXXX) at this path."
}