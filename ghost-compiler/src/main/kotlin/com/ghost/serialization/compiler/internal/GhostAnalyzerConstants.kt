package com.ghost.serialization.compiler.internal

/** What the analyzer recognizes and reports: annotation and type names, qualified names, annotation arguments, and
 * analyzer diagnostics. */
internal object GhostAnalyzerConstants {

    const val STR_ZERO = "0"
    const val STR_ZERO_L = "0L"
    const val STR_ZERO_UL = "0uL"
    const val STR_ZERO_D = "0.0"
    const val STR_ZERO_F = "0.0f"
    const val STR_CHAR_NULL_LITERAL = "'\\u0000'"
    const val STR_DOUBLE_QUOTE = "\""
    const val CHAR_UNDERSCORE = '_'
    const val STR_BANG_BANG = "!!"
    const val TEMPLATE_VAR_NAME = "%sValue"
    const val TEMPLATE_RESULT_VAR = "result.%s"
    const val TEMPLATE_MASK_VAR = "mask%s"
    const val TEMPLATE_CTX_VAR = "ctx.%s"
    const val TEMPLATE_CTX_MASK_VAR = "ctx.mask%s"
    const val TEMPLATE_IF_MASK_RETURN = "if ((%s and %s) != 0L) %s else %s"
    const val TEMPLATE_WRAP_TYPE = "%s(%s)"
    const val K_INT = "kotlin.Int"
    const val K_LONG = "kotlin.Long"
    const val K_ULONG = "kotlin.ULong"
    const val K_UINT = "kotlin.UInt"
    const val K_USHORT = "kotlin.UShort"
    const val K_UBYTE = "kotlin.UByte"
    const val K_STRING = "kotlin.String"
    const val K_BOOLEAN = "kotlin.Boolean"
    const val K_DOUBLE = "kotlin.Double"
    const val K_FLOAT = "kotlin.Float"
    const val K_BYTE = "kotlin.Byte"
    const val K_SHORT = "kotlin.Short"
    const val K_CHAR = "kotlin.Char"
    const val K_UNIT = "kotlin.Unit"
    const val K_NOTHING = "kotlin.Nothing"
    const val K_ANY = "kotlin.Any"
    const val K_BYTE_ARRAY = "kotlin.ByteArray"
    const val K_RAW_JSON = "com.ghost.serialization.types.RawJson"
    const val STR_KOTLIN_PREFIX = "kotlin."
    const val STR_JAVA_PREFIX = "java."
    const val STR_ERR_CLASS_1 =
        "GhostSerialization: @GhostSerialization can only be applied to 'data class', 'sealed class', 'value class' or 'enum class'. "

    const val STR_ERR_CLASS_2 = "Class '"
    const val STR_ERR_CLASS_3 = "' is not supported."
    const val STR_ERR_PRIV_1 =
        "GhostSerialization: Properties in @GhostSerialization classes cannot be private. "

    const val STR_ERR_PRIV_2 = "Please remove 'private' modifier from properties in '"
    const val STR_ERR_PRIV_3 = "'."
    const val STR_ERR_DUP_1 = "GhostSerialization: Duplicate JSON name '"
    const val STR_ERR_DUP_2 = "' found in class '"
    const val STR_ERR_DUP_3 = "'. "
    const val STR_ERR_DUP_4 = "Problematic properties: "
    const val STR_ERR_MAP_1 = "GhostSerialization: Map key must be a String in property '"
    const val STR_ERR_MAP_2 = "'. "
    const val STR_ERR_MAP_3 = "JSON only supports string-keyed objects."
    const val STR_KOTLIN_DOT = "kotlin."
    const val STR_TYPE_INT_ARRAY = "kotlin.IntArray"
    const val STR_TYPE_LONG_ARRAY = "kotlin.LongArray"
    const val STR_TYPE_FLOAT_ARRAY = "kotlin.FloatArray"
    const val STR_TYPE_DOUBLE_ARRAY = "kotlin.DoubleArray"
    const val STR_TYPE_BOOLEAN_ARRAY = "kotlin.BooleanArray"
    const val GHOST_IGNORE = "GhostIgnore"
    const val GHOST_NAME = "GhostName"
    const val SERIAL_NAME = "SerialName"
    const val LIST_QUALIFIED = "kotlin.collections.List"
    const val SET_QUALIFIED = "kotlin.collections.Set"
    const val MAP_QUALIFIED = "kotlin.collections.Map"
    const val STRING_QUALIFIED = "kotlin.String"
    const val STR_VALUE_ARG = "value"
    const val STR_SERIAL_NAME_SUFFIX = "SerialName"
    const val GHOST_RESILIENT = "GhostResilient"
    const val GHOST_DECODER = "GhostDecoder"
    const val GHOST_ENCODER = "GhostEncoder"
    const val PROVIDER_ARG = "provider"
    const val FUNCTION_NAME_ARG = "functionName"
    const val GHOST_FLATTEN = "GhostFlatten"
    const val GHOST_WRAP = "GhostWrap"
    const val GHOST_WRAPPED_KEYS = "GhostWrappedKeys"
    const val KEYS_ARG = "keys"
    const val OMIT_IF_EMPTY_ARG = "omitIfEmpty"
    const val OMIT_IF_ABSENT_ARG = "omitIfAbsent"
    const val PATH_ARG = "path"
    const val NAME = "name"
    const val STR_GHOST_JSON_READER_QUALIFIED = "${GhostCommonConstants.PKG_PARSER_STREAMING}.${GhostCodegenConstants.STR_GHOST_JSON_READER}"
    const val STR_GHOST_JSON_FLAT_READER_QUALIFIED = "${GhostCommonConstants.PKG_PARSER_BYTES}.${GhostEmitterConstants.STR_GHOST_JSON_FLAT_READER}"
    const val STR_GHOST_JSON_STRING_READER_QUALIFIED =
        "${GhostCommonConstants.PKG_PARSER_STRINGS}.${GhostEmitterConstants.STR_GHOST_JSON_STRING_READER}"

    /** `@GhostSerialization(textChannel = …)` argument name. */
    const val ARG_TEXT_CHANNEL = "textChannel"

    const val STR_DEFAULT_DISCRIMINATOR = "type"
    const val GHOST_SIGNATURE = "GhostSignature"
    const val STR_WARN_CUSTOM_CODER = "Detected custom coder for %s: D=%s, E=%s"
    const val STR_ERR_SINGLE_SHOT_DEFAULT_1 = "single-shot requires defaultExpression for "
    const val STR_GHOST_JSON_ENVELOPE = "GhostJsonEnvelope"

    const val STR_GHOST_ENVELOPE_PAYLOAD = "GhostEnvelopePayload"
    const val STR_GHOST_ENVELOPE_FALLBACK = "GhostEnvelopeFallback"
    const val ARG_ENVELOPE_DISCRIMINATOR = "discriminator"
    const val ARG_ENVELOPE_TIME_FIELD = "timeField"
    const val ARG_ENVELOPE_DATA_FIELD = "dataField"
    const val ARG_ENVELOPE_TARGET = "target"
    const val STR_ERR_ENVELOPE_DISC_1 = "GhostJsonEnvelope: discriminator field '"
    const val STR_ERR_ENVELOPE_DISC_2 = "' not found on '"
    const val STR_ERR_ENVELOPE_DISC_3 = "'."
    const val STR_ERR_ENVELOPE_DISC_TYPE_1 = "GhostJsonEnvelope: discriminator property '"
    const val STR_ERR_ENVELOPE_DISC_TYPE_2 = "' must be String."
    const val STR_WARN_ENVELOPE_TIME_1 = "GhostJsonEnvelope: timeField '"
    const val STR_WARN_ENVELOPE_TIME_2 = "' not found; ignoring."
    const val STR_ERR_ENVELOPE_MULTI_FALLBACK =
        "GhostJsonEnvelope: at most one @GhostEnvelopeFallback property is allowed."

    const val STR_ERR_ENVELOPE_DATA_1 = "GhostJsonEnvelope: dataField '"
    const val STR_ERR_ENVELOPE_DATA_2 = "' not found on envelope class."
    const val STR_WARN_ENVELOPE_UNTAGGED_1 = "GhostJsonEnvelope: opaque field '"
    const val STR_WARN_ENVELOPE_UNTAGGED_2 =
        "' has no @GhostEnvelopePayload and will not be routed."

    const val STR_ERR_ENVELOPE_NO_PAYLOADS =
        "GhostJsonEnvelope: no @GhostEnvelopePayload properties found (fat envelope mode)."

    const val STR_ERR_ENVELOPE_DUP_VALUE_1 = "GhostJsonEnvelope: duplicate discriminator value '"
    const val STR_ERR_ENVELOPE_DUP_VALUE_2 = "' on properties: "
    const val STR_ERR_ENVELOPE_PAYLOAD_TYPE_1 = "GhostJsonEnvelope: payload property '"
    const val STR_ERR_ENVELOPE_PAYLOAD_TYPE_2 = "' must be nullable RawJson."
    const val STR_ERR_ENVELOPE_PAYLOAD_VALUE =
        "GhostJsonEnvelope: @GhostEnvelopePayload requires non-empty value."

    const val STR_ERR_WRAPPED_DUP_KEY_1 = "GhostWrappedKeys: duplicate wire key '"
    const val STR_ERR_WRAPPED_DUP_KEY_2 = "' on "
    const val STR_ERR_WRAPPED_DUP_KEY_3 = " (properties '"
    const val STR_ERR_WRAPPED_DUP_KEY_4 = "' and '"
    const val STR_ERR_WRAPPED_DUP_KEY_5 = "')."
    const val STR_ERR_WRAPPED_OMIT_IF_EMPTY_1 =
        "GhostWrappedKeys: omitIfEmpty = true requires nullable property '"

    const val STR_ERR_WRAPPED_OMIT_IF_EMPTY_2 = "'."
    const val STR_ERR_WRAPPED_COMBINE_1 = "GhostWrappedKeys: property '"
    const val STR_ERR_WRAPPED_COMBINE_2 =
        "' cannot combine @GhostWrappedKeys with @GhostFlatten or @GhostWrap."

    const val STR_ERR_WRAPPED_KEY_CONFLICT_1 = "GhostWrappedKeys: wire key '"
    const val STR_ERR_WRAPPED_KEY_CONFLICT_2 =
        "' is both a wrapped source and a regular JSON property on "

    const val STR_ERR_WRAPPED_KEY_CONFLICT_3 = "."
    const val STR_ERR_WRAPPED_EMPTY_KEYS_1 =
        "GhostWrappedKeys: keys must not be empty on property '"

    const val STR_ERR_WRAPPED_EMPTY_KEYS_2 = "'."
    const val STR_WARN_WRAPPED_UNMAPPED_1 = "GhostWrappedKeys: wire key '"
    const val STR_WARN_WRAPPED_UNMAPPED_2 = "' does not map to a field on "
    const val STR_EMPTY_LIST_CALL = "emptyList()"

    const val STR_EMPTY_SET_CALL = "emptySet()"
    const val STR_EMPTY_MAP_CALL = "emptyMap()"
    const val STR_LIST_OF_EMPTY_CALL = "listOf()"
    const val STR_SET_OF_EMPTY_CALL = "setOf()"
    const val STR_MAP_OF_EMPTY_CALL = "mapOf()"
    const val STR_CHAR_ESC_QUOTE = "\\'"
    const val STR_CHAR_ESC_BACKSLASH = "\\\\"
    const val STR_CHAR_ESC_N = "\\n"
    const val STR_CHAR_ESC_T = "\\t"
    const val STR_CHAR_ESC_R = "\\r"
    const val STR_CHAR_ESC_B = "\\b"
    const val STR_CHAR_ESC_DOLLAR = "\\$"
    const val STR_UNICODE_ESC_PREFIX = "\\u"
    const val STR_TRIPLE_QUOTE = "\"\"\""
    const val DEFAULT_EXPR_MAX_PARAM_SEARCH_CHARS = 8_192
    const val DEFAULT_EXPR_MAX_PARAM_BACKTRACK_CHARS = 512
    const val DEFAULT_EXPR_UNICODE_ESC_LEN = 6
    const val DEFAULT_EXPR_UNICODE_HEX_LEN = 4
    const val DEFAULT_EXPR_MIN_CHAR_LITERAL_LEN = 3
    const val DEFAULT_EXPR_MIN_STRING_LITERAL_LEN = 2
    const val REGEX_DEFAULT_EXPR_NUMERIC =
        """^-?(?:""" +
                """(?:0|[1-9]\d*)(?:L|l)?|""" +
                """(?:0|[1-9]\d*)\.\d+(?:[eE][+-]?\d+)?[fFdD]?|""" +
                """(?:0|[1-9]\d*)(?:[eE][+-]?\d+)[fFdD]?|""" +
                """(?:0|[1-9]\d*)[fFdD]""" +
                """)$"""

    const val REGEX_DEFAULT_EXPR_ENUM_REF =
        """^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$"""

    val DEFAULT_EXPR_EMPTY_COLLECTION_CALLS = setOf(
        STR_EMPTY_LIST_CALL,
        STR_EMPTY_SET_CALL,
        STR_EMPTY_MAP_CALL,
        STR_LIST_OF_EMPTY_CALL,
        STR_SET_OF_EMPTY_CALL,
        STR_MAP_OF_EMPTY_CALL,
    )

    val DEFAULT_EXPR_CHAR_ESCAPES = setOf(
        STR_CHAR_ESC_QUOTE,
        STR_CHAR_ESC_BACKSLASH,
        STR_CHAR_ESC_N,
        STR_CHAR_ESC_T,
        STR_CHAR_ESC_R,
        STR_CHAR_ESC_B,
        STR_CHAR_ESC_DOLLAR,
    )

    val DEFAULT_EXPR_SCALAR_KEYWORDS = setOf(GhostCommonConstants.STR_NULL, GhostCommonConstants.STR_TRUE, GhostCommonConstants.STR_FALSE)
}
