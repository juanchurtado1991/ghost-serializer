package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** True for the escape letters JSON allows after a backslash besides `u`: `" \ / b f n r t`. */
internal inline fun isSimpleEscapeCode(
    escaped: Int
): Boolean = escaped == TOK.QUOTE_INT ||
    escaped == TOK.BACKSLASH_INT ||
    escaped == TOK.SLASH_INT ||
    escaped == TOK.B_BYTE_INT ||
    escaped == TOK.F_BYTE_INT ||
    escaped == TOK.N_BYTE_INT ||
    escaped == TOK.R_BYTE_INT ||
    escaped == TOK.T_BYTE_INT
