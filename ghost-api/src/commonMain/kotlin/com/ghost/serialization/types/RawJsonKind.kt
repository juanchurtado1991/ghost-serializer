package com.ghost.serialization.types

/** JSON value category of a [RawJson] payload (first-token classification). */
enum class RawJsonKind {
    /** `{` … `}` */
    OBJECT,

    /** `[` … `]` */
    ARRAY,

    /** `"` … `"` */
    STRING,

    /** JSON number (integer, fraction, or exponent form). */
    NUMBER,

    /** `true` or `false`. */
    BOOLEAN,

    /** `null`. */
    NULL,

    /** Empty, whitespace-only, or syntactically invalid at the first token. */
    INVALID,
}
