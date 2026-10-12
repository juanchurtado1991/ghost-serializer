package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

/** Wire names that must be escaped inside a JSON string, and template/format characters that must not be. */
@GhostSerialization
data class EscapedWireNamesModel(
    @GhostName("a\"b") val quoted: Int,
    @GhostName("back\\slash") val backslash: Int,
    @GhostName("new\nline") val newline: Int,
    @GhostName("\u0001control") val control: Int,
    @GhostName("dollar\${sign}") val dollar: Int,
    @GhostName("percent%s") val percent: Int
)
