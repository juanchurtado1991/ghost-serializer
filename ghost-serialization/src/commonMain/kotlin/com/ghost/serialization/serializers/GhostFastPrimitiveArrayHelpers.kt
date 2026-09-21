package com.ghost.serialization.serializers

import okio.ByteString
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/** True when the [limit]-bounded bytes at [pos] match [literal] byte-for-byte. */
@PublishedApi
internal inline fun matchesLiteral(getByte: (Int) -> Int, pos: Int, limit: Int, literal: ByteString): Boolean {
    val size = literal.size
    if (pos + size > limit) return false
    for (i in 0 until size) {
        if (getByte(pos + i) != (literal[i].toInt() and C.BYTE_MASK)) return false
    }
    return true
}

/**
 * Writes [size] elements via [writeAt]; separator bookkeeping is handled inside each
 * `writer.value(...)` overload, so this is pure iteration shared by every primitive array
 * serializer's write path.
 */
internal inline fun writeArrayElements(size: Int, writeAt: (Int) -> Unit) {
    for (i in 0 until size) writeAt(i)
}

/**
 * Fast path for a compact, comma-separated run of bare integers inside `[...]` (the common,
 * dominant-cost shape for large numeric arrays). Falls back to `null` (position reset to
 * [startPosition]) on anything that doesn't fit — whitespace, decimal point/exponent, digit
 * overflow — so the general element-by-element loop remains the source of truth for overflow
 * and error messages. Like that loop, it does not enforce `maxCollectionSize`.
 *
 * Precondition: called right after `beginArray()` confirms a non-empty array, with
 * [startPosition] at that first byte. On success, position is left AT the closing `]` —
 * callers must still call `endArray()` themselves.
 */
internal inline fun tryFastIntArrayCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
): IntArray? {
    var pos = startPosition
    val list = GhostIntList()
    while (true) {
        var negative = false
        if (pos < limit && getByte(pos) == C.MINUS_INT) {
            negative = true
            pos++
        }
        val digitsStart = pos
        var value = 0
        var digitCount = 0
        while (pos < limit) {
            val b = getByte(pos)
            if (b < C.ZERO_INT || b > C.NINE_INT) break
            if (digitCount >= C.INT_SAFE_DIGITS) {
                setPosition(startPosition)
                return null
            }
            value = value * C.BASE_TEN + (b - C.ZERO_INT)
            digitCount++
            pos++
        }
        if (pos == digitsStart) {
            setPosition(startPosition)
            return null
        }
        list.add(if (negative) -value else value)
        when {
            pos < limit && getByte(pos) == C.COMMA_INT -> {
                pos++
            }

            pos < limit && getByte(pos) == C.CLOSE_ARR_INT -> {
                setPosition(pos)
                return list.toArray()
            }

            else -> {
                setPosition(startPosition)
                return null
            }
        }
    }
}

/** Same fast path as [tryFastIntArrayCore], for a run of bare `Long`s. */
internal inline fun tryFastLongArrayCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
): LongArray? {
    var pos = startPosition
    val list = GhostLongList()
    while (true) {
        var negative = false
        if (pos < limit && getByte(pos) == C.MINUS_INT) {
            negative = true
            pos++
        }
        val digitsStart = pos
        var value = 0L
        var digitCount = 0
        while (pos < limit) {
            val b = getByte(pos)
            if (b < C.ZERO_INT || b > C.NINE_INT) break
            if (digitCount >= C.LONG_SAFE_DIGITS) {
                setPosition(startPosition)
                return null
            }
            value = value * C.BASE_TEN + (b - C.ZERO_INT)
            digitCount++
            pos++
        }
        if (pos == digitsStart) {
            setPosition(startPosition)
            return null
        }
        list.add(if (negative) -value else value)
        when {
            pos < limit && getByte(pos) == C.COMMA_INT -> {
                pos++
            }

            pos < limit && getByte(pos) == C.CLOSE_ARR_INT -> {
                setPosition(pos)
                return list.toArray()
            }

            else -> {
                setPosition(startPosition)
                return null
            }
        }
    }
}

/**
 * Fast path for a compact, comma-separated run of bare `Double`/`Float` numbers. Unlike
 * [tryFastIntArrayCore], it doesn't reimplement number parsing — it just peeks that the next
 * byte plausibly starts a number and delegates the scan to [parseNext] (`nextDouble`/`nextFloat`),
 * the single source of truth for numeric grammar. Falls back to `false` (position reset to
 * [startPosition]) the moment the shape doesn't fit.
 *
 * Precondition: same as [tryFastIntArrayCore]; [parseNext] must read from, and leave the reader
 * positioned right after, whatever [getPosition] reports.
 */
internal inline fun <T> tryFastDecimalArrayCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    addTo: MutableList<T>,
    parseNext: () -> T,
): Boolean {
    var checkPosition = startPosition
    while (true) {
        if (checkPosition >= limit) {
            setPosition(startPosition)
            return false
        }
        val first = getByte(checkPosition)
        if (first != C.MINUS_INT && (first < C.ZERO_INT || first > C.NINE_INT)) {
            setPosition(startPosition)
            return false
        }
        addTo.add(parseNext())
        val afterPosition = getPosition()
        if (afterPosition < limit && getByte(afterPosition) == C.COMMA_INT) {
            checkPosition = afterPosition + 1
            setPosition(checkPosition)
            continue
        }
        if (afterPosition < limit && getByte(afterPosition) == C.CLOSE_ARR_INT) {
            return true
        }
        setPosition(startPosition)
        return false
    }
}

/**
 * Fast path for a compact, comma-separated run of bare `true`/`false` literals — bypasses the
 * per-element `hasNext()`/comma-bookkeeping dispatch. Does not activate for coerced boolean
 * values (`1`/`0`, quoted strings); those fall back to the general loop.
 *
 * Precondition: same as [tryFastIntArrayCore].
 */
internal inline fun tryFastBooleanArrayCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
): BooleanArray? {
    var pos = startPosition
    val list = GhostBooleanList()
    while (true) {
        val value: Boolean
        if (matchesLiteral(getByte, pos, limit, C.TRUE_BS)) {
            value = true
            pos += C.TRUE_BS.size
        } else if (matchesLiteral(getByte, pos, limit, C.FALSE_BS)) {
            value = false
            pos += C.FALSE_BS.size
        } else {
            setPosition(startPosition)
            return null
        }
        list.add(value)
        when {
            pos < limit && getByte(pos) == C.COMMA_INT -> {
                pos++
            }

            pos < limit && getByte(pos) == C.CLOSE_ARR_INT -> {
                setPosition(pos)
                return list.toArray()
            }

            else -> {
                setPosition(startPosition)
                return null
            }
        }
    }
}
