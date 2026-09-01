package com.ghost.serialization.parser.strings

import com.ghost.serialization.parser.common.GhostJsonConstants as C

private const val CHAR_OFFSET_1 = 1
private const val CHAR_OFFSET_2 = 2
private const val CHAR_OFFSET_3 = 3

/**
 * Packs [C.LONG_CHARS] UTF-16 code units starting at [index] into one [Long] ([C.SHIFT_16] bits
 * per char, `chars[index]` in the low bits). Pure Kotlin bit-packing — no memory
 * reinterpretation, so (unlike the byte-channel `ghostReadLong8`) this needs no `expect`/`actual`:
 * the layout is chosen here, not platform-defined. Caller must guarantee
 * `index + C.LONG_CHARS <= chars.size`.
 */
internal inline fun packChars4(chars: CharArray, index: Int): Long =
    chars[index].code.toLong() or
        (chars[index + CHAR_OFFSET_1].code.toLong() shl C.SHIFT_16) or
        (chars[index + CHAR_OFFSET_2].code.toLong() shl C.SHIFT_32) or
        (chars[index + CHAR_OFFSET_3].code.toLong() shl C.SHIFT_48)

/**
 * `ghostCharSwarLengthMasks[n]` keeps the low `n` packed chars ([C.SHIFT_16] bits each) of a
 * [packChars4] result, zeroing the rest — masking `packChars4(chars, i) and
 * ghostCharSwarLengthMasks[n]` compares only the first `n` chars at `i`.
 */
internal val ghostCharSwarLengthMasks: LongArray = LongArray(C.LONG_CHARS + 1) { n ->
    if (n >= C.LONG_CHARS) -1L else (1L shl (C.SHIFT_16 * n)) - 1L
}
