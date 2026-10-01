package com.ghost.serialization.yaml

/** Error message text (and fragments) for YAML parse/write failures and serializer lookup. */
@PublishedApi
internal object GhostYamlErrorMessages {

    // ── Error messages (parser / writer / extensions) ────────────────────────

    const val ERR_EXPECTED_MAP_PREFIX = "Expected Map but found "
    const val ERR_EXPECTED_INT_PREFIX = "Expected Int but found "
    const val ERR_EXPECTED_LONG_PREFIX = "Expected Long but found "
    const val ERR_EXPECTED_ULONG_PREFIX = "Expected ULong but found "
    const val ERR_EXPECTED_DOUBLE_PREFIX = "Expected Double but found "
    const val ERR_EXPECTED_FLOAT_PREFIX = "Expected Float but found "
    const val ERR_EXPECTED_BOOLEAN_PREFIX = "Expected Boolean but found "
    const val ERR_EXPECTED_SINGLE_CHAR_LEN_PREFIX = "Expected single-character string but found length "
    const val ERR_EXPECTED_LIST_PREFIX = "Expected List but found "
    const val ERR_PLAIN_SCALAR_PERCENT =
        "A plain scalar cannot start with '%' — reserved for directives"
    const val ERR_MAX_NESTING_DEPTH_PREFIX = "Maximum nesting depth ("
    const val ERR_MAX_NESTING_DEPTH_SUFFIX = ") exceeded"
    const val ERR_TAB_IN_BLOCK_MAPPING_INDENT =
        "Tab character not allowed in block mapping indentation"

    /** YAML [GhostYamlException] position suffix: `"$message$ERR_AT_POSITION_PAREN_PREFIX$pos$ERR_AT_POSITION_PAREN_SUFFIX"`. */
    const val ERR_AT_POSITION_PAREN_PREFIX = " (position="
    const val ERR_AT_POSITION_PAREN_SUFFIX = ")"
    const val ERR_EXPECTED_COLON_AFTER_KEY_PREFIX = "Expected ':' after key '"
    const val ERR_EXPECTED_COLON_AFTER_KEY_MID = "' at position "
    const val ERR_TAB_IN_BLOCK_SEQUENCE_INDENT =
        "Tab character not allowed in block sequence indentation"
    const val ERR_UNEXPECTED_INLINE_NESTED_MAPPING_COLON =
        "Unexpected ':' — a nested mapping can't start inline on the same line as its enclosing key"
    const val ERR_LONE_DASH_IN_FLOW =
        "A lone '-' is not a valid plain scalar in flow context"
    const val ERR_PLAIN_CONTINUATION_MAPPING_KEY =
        "Plain scalar continuation cannot contain a mapping key indicator"
    const val ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY =
        "Anchor/tag prefix on a key must be followed by the key itself"
    const val ERR_IMPLICIT_KEY_MULTILINE = "Implicit keys cannot span multiple lines"
    const val ERR_PARSER_NO_PROGRESS_PREFIX = "Parser made no progress at position "
    const val ERR_PARSER_NO_PROGRESS_SUFFIX = " — malformed content"
    const val ERR_UNEXPECTED_AFTER_DOCUMENT_VALUE = "Unexpected content after document value"
    const val ERR_DIRECTIVES_NEED_DOC_END =
        "Directives must be preceded by an explicit document end marker (...)"
    const val ERR_UNEXPECTED_COMMA_FLOW_MAPPING =
        "Unexpected ',' in flow mapping — empty entries are not allowed"
    const val ERR_EXPECTED_COLON_AFTER_FLOW_KEY_PREFIX = "Expected ':' after flow mapping key '"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_MAP = "Expected ',' or '}' in flow mapping"
    const val ERR_UNEXPECTED_COMMA_FLOW_SEQUENCE =
        "Unexpected ',' in flow sequence — empty entries are not allowed"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_SEQ = "Expected ',' or ']' in flow sequence"
    const val ERR_UNTERMINATED_DOUBLE_QUOTED = "Unterminated double-quoted string"
    const val ERR_UNTERMINATED_SINGLE_QUOTED = "Unterminated single-quoted string"
    const val ERR_DOC_MARKER_IN_QUOTED_SCALAR_PREFIX = "Document marker '"
    const val ERR_DOC_MARKER_IN_QUOTED_SCALAR_SUFFIX = "' not allowed inside a quoted scalar"
    const val ERR_INCOMPLETE_X_ESCAPE = "Incomplete \\x escape"
    const val ERR_INCOMPLETE_U_ESCAPE = "Incomplete \\u escape"
    const val ERR_INCOMPLETE_U32_ESCAPE = "Incomplete \\U escape"
    const val ERR_UNKNOWN_ESCAPE_PREFIX = "Unknown escape: \\"
    const val ERR_INVALID_HEX_IN_ESCAPE = "Invalid hex char in escape sequence"
    const val ERR_BLOCK_INDENT_INDICATOR_DIGIT =
        "Block scalar indentation indicator must be a single digit"
    const val ERR_BLOCK_INDENT_RANGE_1_9 =
        "Block scalar indentation indicator must be between 1 and 9"
    const val ERR_COMMENT_AFTER_BLOCK_INDICATOR_WS =
        "Comment after block scalar indicator must be preceded by whitespace"
    const val ERR_INVALID_TEXT_AFTER_BLOCK_INDICATOR = "Invalid text after block scalar indicator"
    const val ERR_LEADING_EMPTY_LINE_OVERINDENTED =
        "Leading empty line in block scalar is more indented than its first content line"
    const val ERR_EOF_AFTER_TAG = "Unexpected end of input after tag indicator"
    const val ERR_TAG_HANDLE_UNDEFINED_PREFIX = "Tag handle '"
    const val ERR_TAG_HANDLE_UNDEFINED_SUFFIX =
        "' is not defined by a %TAG directive in this document"
    const val ERR_INVALID_CHAR_AFTER_TAG = "Invalid character immediately after tag"
    const val ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX = "Anchor '"
    const val ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX = "' cannot be immediately followed by an alias"
    const val ERR_ANCHOR_FOLLOWED_BY_SEQ_SUFFIX =
        "' cannot be immediately followed by a block sequence entry on the same line"
    const val ERR_ANCHOR_NOT_FOUND_PREFIX = "Anchor '"
    const val ERR_ANCHOR_NOT_FOUND_SUFFIX = "' not found"
    const val ERR_COMMENT_NEEDS_WHITESPACE = "Comment must be preceded by whitespace"
    const val ERR_DUPLICATE_YAML_DIRECTIVE = "Duplicate %YAML directive"
    const val ERR_MALFORMED_YAML_VERSION_PREFIX = "Malformed %YAML directive version: "
    const val ERR_UNEXPECTED_AFTER_YAML_DIRECTIVE = "Unexpected content after %YAML directive"
    const val ERR_DIRECTIVES_NEED_DOC_START =
        "Directives must be followed by a document-start marker (---)"
    const val ERR_UNEXPECTED_AFTER_DOC_END = "Unexpected content after document-end marker"
    const val ERR_MAX_DEPTH_EXCEEDED = "Max depth exceeded"
    const val ERR_NAME_OUTSIDE_OBJECT = "Cannot write name outside of object scope"
    const val ERR_SERIALIZER_NOT_FOUND_PREFIX = "Serializer not found for "
    const val ERR_NOT_YAML_SERIALIZER_PREFIX = "Serializer for "
    const val ERR_NOT_YAML_SERIALIZER_SUFFIX = " does not implement GhostYamlSerializer"
    const val STR_UNKNOWN_TYPE = "unknown"
}
