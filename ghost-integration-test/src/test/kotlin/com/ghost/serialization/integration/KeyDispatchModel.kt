package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class KeyDispatchModel(
    @GhostName("a") val p0: Int? = null,
    @GhostName("ab") val p1: Int? = null,
    @GhostName("abc") val p2: Int? = null,
    @GhostName("abcd") val p3: Int? = null,
    @GhostName("abcdefg") val p4: Int? = null,
    @GhostName("abcdefgh") val p5: Int? = null,
    @GhostName("abcdefghi") val p6: Int? = null,
    @GhostName("abcdefghij") val p7: Int? = null,
    @GhostName("abcdefghijklmnopq") val p8: Int? = null,
    @GhostName("id") val p9: Int? = null,
    @GhostName("Id") val p10: Int? = null,
    @GhostName("ID") val p11: Int? = null,
    @GhostName("idx") val p12: Int? = null,
    @GhostName("name") val p13: Int? = null,
    @GhostName("Name") val p14: Int? = null,
    @GhostName("na") val p15: Int? = null,
    @GhostName("ne") val p16: Int? = null,
    @GhostName("x1") val p17: Int? = null,
    @GhostName("x2") val p18: Int? = null,
    @GhostName("x10") val p19: Int? = null,
    @GhostName("x11") val p20: Int? = null,
    @GhostName("keyOne") val p21: Int? = null,
    @GhostName("keyTwo") val p22: Int? = null,
    @GhostName("A") val p23: Int? = null,
    @GhostName("B") val p24: Int? = null,
    @GhostName("key-1") val p25: Int? = null,
    @GhostName("key_1") val p26: Int? = null,
    @GhostName("a.b") val p27: Int? = null,
    @GhostName("kéy") val p28: Int? = null,
    @GhostName("日本") val p29: Int? = null,
    @GhostName("😀") val p30: Int? = null,
    @GhostName("with space") val p31: Int? = null,
    @GhostName("q\"q") val p32: Int? = null,
    @GhostName("a/b") val p33: Int? = null,
    @GhostName("long_key_name_that_is_over_thirty_two_chars_long") val p34: Int? = null,
)
