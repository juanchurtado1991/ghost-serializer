package com.ghost.serialization.yaml.exception

object GhostYamlHintMessages {
    const val HINT_ANCHOR_NOT_FOUND =
        "Define the anchor with &name before referencing it with *name in this document."
    const val HINT_COERCE_BOOLEANS =
        "If the API sends string booleans, enable coerceBooleans on the reader options."
    const val HINT_EXPECTED_LIST =
        "Expected a YAML sequence/list here — check the value type at this path."
    const val HINT_EXPECTED_MAP =
        "Expected a YAML mapping here — check the value type at this path."
    const val HINT_EXPECTED_SCALAR =
        "Check the scalar type at this path. For numeric strings, enable coerceStringsToNumbers."
    const val HINT_MAX_NESTING_DEPTH =
        "Reduce nesting, or raise maxDepth on the YAML reader if this document is intentionally deep."
}
