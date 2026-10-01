@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.bytes.extensions

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostHeuristics.initialCollectionCapacity
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Decodes a JSON array into a [List] using [itemParser]. Enforces [GhostJsonFlatReader.maxCollectionSize]. */
inline fun <T> GhostJsonFlatReader.readList(crossinline itemParser: () -> T): List<T> {
    beginArray()
    if (peekNextToken() == TOK.CLOSE_ARR_INT) {
        endArray()
        return emptyList()
    }
    val list = ArrayList<T>(initialCollectionCapacity)
    val maxSize = maxCollectionSize

    while (true) {
        list.add(itemParser())
        val next = nextNonWhitespace()
        if (next == TOK.CLOSE_ARR_INT) {
            if (depth > 0) depth--
            break
        }
        if (next != TOK.COMMA_INT) {
            throwError("${EM.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found $next")
        }
        if (list.size > maxSize) {
            throwError("${EM.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
        }
    }
    return list
}

/** Reads a JSON array into a [Set] without an intermediate [List] allocation. */
inline fun <T> GhostJsonFlatReader.readSet(crossinline itemParser: () -> T): Set<T> {
    beginArray()
    if (peekNextToken() == TOK.CLOSE_ARR_INT) {
        endArray()
        return emptySet()
    }
    val set = HashSet<T>(initialCollectionCapacity)
    val maxSize = maxCollectionSize

    while (true) {
        set.add(itemParser())
        val next = nextNonWhitespace()
        if (next == TOK.CLOSE_ARR_INT) {
            if (depth > 0) depth--
            break
        }
        if (next != TOK.COMMA_INT) {
            throwError("${EM.ERR_EXPECTED_COMMA_OR_CLOSE_ARR} but found $next")
        }
        if (set.size > maxSize) {
            throwError("${EM.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
        }
    }
    return set
}

/** Decodes a JSON object into a [Map] using [keyParser]/[valueParser].
 * Enforces [GhostJsonFlatReader.maxCollectionSize]. */
inline fun <K, V> GhostJsonFlatReader.readMap(
    crossinline keyParser: () -> K,
    crossinline valueParser: () -> V
): Map<K, V> {
    beginObject()
    if (peekNextToken() == TOK.CLOSE_OBJ_INT) {
        endObject()
        return emptyMap()
    }

    val map = HashMap<K, V>(initialCollectionCapacity)
    val maxSize = maxCollectionSize

    while (true) {
        val key = keyParser()
        consumeKeySeparator()
        val value = valueParser()
        map[key] = value

        val next = nextNonWhitespace()
        if (next == TOK.CLOSE_OBJ_INT) {
            if (depth > 0) depth--
            break
        }
        if (next != TOK.COMMA_INT) {
            throwError("${EM.ERR_EXPECTED_COMMA_OR_CLOSE_OBJ} but found $next")
        }
        // The comma was consumed directly via nextNonWhitespace(); clear needsCommaMask so
        // the next keyParser() (nextKey()) doesn't re-require another comma.
        if (depth < SCN.MAX_BITMASK_DEPTH) {
            val bit = SCN.BITMASK_UNIT shl depth
            needsCommaMask = needsCommaMask and bit.inv()
        }
        if (map.size > maxSize) {
            throwError("${EM.ERR_MAX_COLLECTION_SIZE} ($maxSize)")
        }
    }
    return map
}

/** Runs [block]; on [GhostJsonException] rolls back state and skips the invalid value, returning `null`. */
@InternalGhostApi
inline fun <T> GhostJsonFlatReader.decodeResilient(crossinline block: () -> T): T? {
    val savedPos = position
    val savedToken = nextTokenByte
    val savedDepth = depth
    val savedNeedsCommaMask = needsCommaMask
    val savedCommaConsumedMask = commaConsumedMask
    try {
        return block()
    } catch (_: GhostJsonException) {
        position = savedPos
        nextTokenByte = savedToken
        depth = savedDepth
        needsCommaMask = savedNeedsCommaMask
        commaConsumedMask = savedCommaConsumedMask
        skipValue()
        return null
    }
}
