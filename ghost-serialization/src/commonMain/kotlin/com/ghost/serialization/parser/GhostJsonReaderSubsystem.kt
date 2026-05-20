@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * Starts a new JSON object.
 * Increments [depth] and validates against [maxDepth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.beginObject() {
    beginObjectImpl(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        maxDepth = maxDepth,
        throwError = { throwError(it) }
    )
}

/**
 * Ends the current JSON object.
 * Decrements [depth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.endObject() {
    endObjectImpl(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) }
    )
}

/**
 * Starts a new JSON array.
 * Increments [depth] and validates against [maxDepth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.beginArray() {
    beginArrayImpl(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        maxDepth = maxDepth,
        throwError = { throwError(it) }
    )
}

/**
 * Ends the current JSON array.
 * Decrements [depth].
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.endArray() {
    endArrayImpl(
        nextNonWhitespace = { nextNonWhitespace() },
        getDepth = { depth },
        setDepth = { depth = it },
        throwError = { throwError(it) }
    )
}

/**
 * Returns true if the current object or array has more elements.
 * Automatically handles comma consumption.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.hasNext(): Boolean {
    return hasNextImpl(
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        throwError = { throwError(it) }
    )
}

fun GhostJsonReader.nextKey(): String? {
    return nextKeyImpl(
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        readQuotedString = { readQuotedString() },
        throwError = { throwError(it) }
    )
}

fun GhostJsonReader.consumeKeySeparator() {
    consumeKeySeparatorImpl(
        nextNonWhitespace = { nextNonWhitespace() },
        throwError = { throwError(it) }
    )
}

fun GhostJsonReader.consumeArraySeparator() {
    consumeArraySeparatorImpl(
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) }
    )
}

/**
 * Reads the next boolean value.
 * Supports string coercion if [coerceBooleans] is true.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.nextBoolean(): Boolean {
    return nextBooleanImpl(
        peekNextToken = { peekNextToken() },
        skipAndValidateLiteral = { skipAndValidateLiteral(it) },
        coerceBooleans = coerceBooleans,
        internalSkip = { internalSkip(it) },
        readQuotedString = { readQuotedString() },
        throwError = { throwError(it) }
    )
}

fun GhostJsonReader.nextString(): String = readQuotedString()

/**
 * Returns true if the next value is null.
 * Used by KSP-generated serializers for nullable properties.
 */
fun GhostJsonReader.isNextNullValue(): Boolean =
    peekNextToken() == C.NULL_CHAR_INT

/**
 * Consumes the null literal from the source.
 * Used by KSP-generated serializers.
 */
fun GhostJsonReader.consumeNull() {
    skipAndValidateLiteral(C.NULL_BS)
}

/**
 * High-performance field identification using pre-calculated [JsonReaderOptions].
 * This is the heart of the generated deserializers, using a 4-byte hash to avoid string comparisons.
 * Returns the index of the field in [options] strings, or [GhostJsonConstants.MATCH_NONE].
 */
fun GhostJsonReader.selectNameAndConsume(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = true)

fun GhostJsonReader.selectString(options: JsonReaderOptions): Int =
    internalSelect(options, consumeSeparator = false)

private fun GhostJsonReader.internalSelect(
    options: JsonReaderOptions,
    consumeSeparator: Boolean
): Int {
    return internalSelectImpl(
        options = options,
        consumeSeparator = consumeSeparator,
        peekNextToken = { peekNextToken() },
        internalSkip = { internalSkip(it) },
        getPosition = { position },
        setPosition = { position = it },
        getLimit = { limit },
        findClosingQuote = { start, limit -> source.findClosingQuote(start, limit) },
        computeKeyHash = { start, length ->
            computeKeyHashImpl(start, length) {
                if (isStreaming) source[it] else rawData[it].toInt() and C.BYTE_MASK
            }
        },
        verifyKeyMatch = { start, length, expected, consumeSep ->
            verifyKeyMatchImpl(
                start = start,
                length = length,
                expected = expected,
                consumeSeparator = consumeSep,
                setPosition = { position = it },
                setNextTokenByte = { nextTokenByte = it },
                getLimit = { limit },
                getByte = {
                    if (isStreaming) source[it] else rawData[it].toInt() and C.BYTE_MASK
                },
                consumeKeySeparator = { consumeKeySeparator() },
                contentEquals = { startPos, expectedStr -> source.contentEquals(startPos, expectedStr) }
            )
        },
        setNextTokenByte = { nextTokenByte = it },
        getByte = {
            if (isStreaming) source[it] else rawData[it].toInt() and C.BYTE_MASK
        },
        consumeKeySeparator = { consumeKeySeparator() },
        strictMode = strictMode,
        decodeToString = { start, end -> source.decodeToString(start, end) },
        throwError = { throwError(it) }
    )
}

/**
 * Searches for a specific key in the current object without fully consuming it.
 * Used for sealed class discriminators.
 * Highly optimized to avoid unnecessary allocations.
 */
fun GhostJsonReader.peekStringField(name: String): String? {
    return peekDiscriminator(name)
}

fun GhostJsonReader.skipValue() {
    skipValueImpl(
        peekNextToken = { peekNextToken() },
        beginObject = { beginObject() },
        hasNext = { hasNext() },
        skipQuotedString = { skipQuotedString() },
        consumeKeySeparator = { consumeKeySeparator() },
        skipValue = { skipValue() },
        endObject = { endObject() },
        beginArray = { beginArray() },
        endArray = { endArray() },
        skipAndValidateLiteral = { skipAndValidateLiteral(it) },
        skipNumber = { skipNumber() },
        throwError = { throwError(it) }
    )
}

inline fun <T> GhostJsonReader.readList(crossinline itemParser: () -> T): List<T> {
    return readListImpl(
        beginArray = { beginArray() },
        peekNextToken = { peekNextToken() },
        endArray = { endArray() },
        getInitialCollectionCapacity = { initialCollectionCapacity },
        getMaxCollectionSize = { maxCollectionSize },
        itemParser = { itemParser() },
        nextNonWhitespace = { nextNonWhitespace() },
        decrementDepth = { depth-- },
        throwError = { throwError(it) }
    )
}

inline fun <K, V> GhostJsonReader.readMap(
    crossinline keyParser: () -> K,
    crossinline valueParser: () -> V
): Map<K, V> {
    return readMapImpl(
        beginObject = { beginObject() },
        peekNextToken = { peekNextToken() },
        endObject = { endObject() },
        getInitialCollectionCapacity = { initialCollectionCapacity },
        getMaxCollectionSize = { maxCollectionSize },
        keyParser = { keyParser() },
        consumeKeySeparator = { consumeKeySeparator() },
        valueParser = { valueParser() },
        nextNonWhitespace = { nextNonWhitespace() },
        decrementDepth = { depth-- },
        throwError = { throwError(it) }
    )
}

@InternalGhostApi
inline fun <T> GhostJsonReader.decodeResilient(crossinline block: () -> T): T? {
    return decodeResilientImpl(
        getPosition = { position },
        setPosition = { position = it },
        getNextTokenByte = { nextTokenByte },
        setNextTokenByte = { nextTokenByte = it },
        skipValue = { skipValue() },
        block = { block() }
    )
}
