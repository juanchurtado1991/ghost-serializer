@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Pure, reader-agnostic boolean coercion matcher: compares raw bytes at [start] against known
 * truthy ("true","yes","on","y","1") / falsy ("false","no","off","n","0") strings, case-insensitive
 * via `or CASE_INSENSITIVE_MASK`, without allocating a String.
 *
 * @param onError Called on no match; must throw ([Nothing] keeps every branch exhaustive).
 */
internal inline fun matchCoerceBooleanBytes(
    start: Int,
    length: Int,
    onError: () -> Nothing,
    getByte: (Int) -> Int,
): Boolean = when (length) {
    TOK.BOOL_STR_LEN_1 -> {
        val b0 = getByte(start)
        when (b0 or TOK.CASE_INSENSITIVE_MASK) {
            TOK.FOLD_Y -> true // "y" / "Y"
            TOK.FOLD_N -> false // "n" / "N"
            else -> when (b0) {
                TOK.ONE_INT -> true // "1"
                TOK.ZERO_INT -> false // "0"
                else -> onError()
            }
        }
    }

    TOK.BOOL_STR_LEN_2 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val isOn = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_O &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_N
        val isNo = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_N &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_O
        when {
            isOn -> true
            isNo -> false
            else -> onError()
        }
    }

    TOK.BOOL_STR_LEN_3 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val isYes = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_Y &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_E &&
            (b2 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_S
        val isOff = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_O &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_F &&
            (b2 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_F
        when {
            isYes -> true
            isOff -> false
            else -> onError()
        }
    }

    TOK.BOOL_STR_LEN_4 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        val isTrue = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_T &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_R &&
            (b2 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_U &&
            (b3 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_E
        if (isTrue) true else onError()
    }

    TOK.BOOL_STR_LEN_5 -> {
        val b0 = getByte(start)
        val b1 = getByte(start + 1)
        val b2 = getByte(start + 2)
        val b3 = getByte(start + 3)
        val b4 = getByte(start + 4)
        val isFalse = (b0 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_F &&
            (b1 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_A &&
            (b2 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_L &&
            (b3 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_S &&
            (b4 or TOK.CASE_INSENSITIVE_MASK) == TOK.FOLD_E
        if (isFalse) false else onError()
    }

    else -> onError()
}
