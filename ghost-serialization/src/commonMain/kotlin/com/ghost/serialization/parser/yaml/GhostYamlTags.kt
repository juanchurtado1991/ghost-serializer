package com.ghost.serialization.parser.yaml

/** Core-schema tag types a `!!name`/bare `!` tag can resolve to; drives [interpretScalar]'s dispatch. */
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
