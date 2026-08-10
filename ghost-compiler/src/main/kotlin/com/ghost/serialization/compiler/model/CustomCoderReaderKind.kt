package com.ghost.serialization.compiler.model

/**
 * Reader parameter type resolved from a custom coder function signature.
 */
internal enum class CustomCoderReaderKind {
    /** `fun(GhostJsonReader): T` — bytes / streaming channel. */
    BYTES,

    /** `fun(GhostJsonFlatReader): T` — flat byte buffer channel. */
    FLAT,

    /** `fun(GhostJsonStringReader): T` — native string channel (textChannel). */
    STRING,
}
