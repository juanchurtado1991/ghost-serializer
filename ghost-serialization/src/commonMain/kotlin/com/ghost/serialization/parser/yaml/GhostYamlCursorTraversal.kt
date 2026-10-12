package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.matchCoerceBooleanBytes
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.exception.hintForYamlError
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Implementation behind [GhostYamlFlatReader]'s `JsonReader`-compatible cursor API
 * (`beginObject`/`endObject`/`nextString`/etc.) — a second-phase facade walking the already-fully
 * parsed in-memory `Map`/`List` AST from [GhostYamlFlatReader.readDocument] via plain iterators.
 * No byte-level scanning happens here.
 *
 * Every [GhostYamlFlatReader] method is a thin delegate to the identically-named `xxxImpl`
 * function here rather than a plain extension function like other subsystems use: `beginObject`
 * etc. are public members called by KSP-generated `deserialize()` bodies that may live in a
 * *different Gradle module's package*, and class members resolve via receiver type with zero
 * imports, while extension functions would require an import the generated code never gains.
 * The state fields (`traversalStack`, `currentMap`, etc.) stay on `GhostYamlFlatReader` itself
 * since Kotlin classes can't span files.
 */

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.ensureRootParsed() {
    if (!rootParsed) {
        rootObject = readDocument()
        nextValue = rootObject
        rootParsed = true
    }
}

/** Called once per document by [GhostYamlFlatReader.readAllDocuments] (typed overload). */
@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.prepareRootForCurrentDocument() {
    clearTraversalState()
    rootObject = readValue(indent = SC.INDENT_UNSET, inFlow = false)
    nextValue = rootObject
    rootParsed = true
}

/** Called once per document by [GhostYamlFlatReader.readAllDocuments] (typed overload). */
@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.clearAfterDocument() {
    clearTraversalState()
    rootParsed = false
    rootObject = null
}

/** Resets every cursor-traversal field to "nothing parsed yet", shared by both functions above. */
private fun GhostYamlFlatReader.clearTraversalState() {
    traversalStack.clear()
    pathTracker.reset()
    currentMap = null
    mapIterator = null
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.beginObjectImpl() {
    ensureRootParsed()
    val map = nextValue as? Map<*, *>
        ?: throwError("${EM.ERR_EXPECTED_MAP_PREFIX}$nextValue")

    pathTracker.pushObject()
    pushTraversalFrame()

    @Suppress("UNCHECKED_CAST")
    val typedMap = map as Map<String, Any?>
    currentMap = typedMap
    mapIterator = typedMap.entries.iterator()
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.endObjectImpl() {
    popTraversalFrameOrClear()
    pathTracker.finishObjectValue()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.selectNameAndConsumeImpl(options: JsonReaderOptions): Int {
    val iterator = mapIterator ?: return tokenEndObject
    if (!iterator.hasNext()) {
        return tokenEndObject
    }
    val entry = iterator.next()
    currentEntry = entry
    nextValue = entry.value

    val index = options.findOptionIndex(name = entry.key)
    if (index >= 0) {
        pathTracker.pushKey(name = entry.key)
        return index
    }
    return tokenUnknownName
}

internal fun GhostYamlFlatReader.selectStringImpl(options: JsonReaderOptions): Int {
    val strValue = nextString()
    val index = options.findOptionIndex(name = strValue)
    if (index >= 0) {
        return index
    }
    return tokenEndObject
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.skipValueImpl() {
    nextValue = null
    // Drop the owning key when this skip follows a successful selectNameAndConsume / nextKey.
    // Unknown keys (-2) never push, so this is a no-op for them.
    pathTracker.finishScalarValue()
}

internal fun GhostYamlFlatReader.isNextNullValueImpl(): Boolean {
    ensureRootParsed()
    return nextValue == null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.consumeNullImpl() {
    nextValue = null
    pathTracker.finishScalarValue()
}

internal fun GhostYamlFlatReader.nextStringOrNullImpl(): String? = nextOrNullImpl { nextString() }
internal fun GhostYamlFlatReader.nextIntOrNullImpl(): Int? = nextOrNullImpl { nextInt() }
internal fun GhostYamlFlatReader.nextLongOrNullImpl(): Long? = nextOrNullImpl { nextLong() }
internal fun GhostYamlFlatReader.nextBooleanOrNullImpl(): Boolean? = nextOrNullImpl { nextBoolean() }

/** Takes the pending value for a scalar read, parsing the document first when the read is the root. */
private fun GhostYamlFlatReader.takeNextValue(): Any? {
    ensureRootParsed()
    val value = nextValue
    nextValue = null
    return value
}

/** Shared null-or-value preamble for the `next*OrNullImpl` family above and below. */
private inline fun <T> GhostYamlFlatReader.nextOrNullImpl(readValue: () -> T): T? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return readValue()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextIntImpl(): Int {
    val value = takeNextValue()
    val wholeNumber = wholeNumberOrNull(value = value)
    val isInIntRange = wholeNumber != null && wholeNumber >= Int.MIN_VALUE && wholeNumber <= Int.MAX_VALUE
    if (!isInIntRange) {
        throwError("${EM.ERR_EXPECTED_INT_PREFIX}$value")
    }
    pathTracker.finishScalarValue()
    return wholeNumber.toInt()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextLongImpl(): Long {
    val value = takeNextValue()
    val wholeNumber = wholeNumberOrNull(value = value)
        ?: throwError("${EM.ERR_EXPECTED_LONG_PREFIX}$value")
    pathTracker.finishScalarValue()
    return wholeNumber
}

internal fun GhostYamlFlatReader.nextProtoUInt64Impl(): ULong {
    val previous = coerceStringsToNumbers
    coerceStringsToNumbers = true
    return try {
        val text = nextString()
        text.toULongOrNull() ?: throwError("${EM.ERR_EXPECTED_ULONG_PREFIX}$text")
    } finally {
        coerceStringsToNumbers = previous
    }
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextULongImpl(): ULong {
    val value = takeNextValue()
    val unsignedNumber = when (value) {
        is String -> value.toULongOrNull()
        is Long -> value.takeIf { it >= 0L }?.toULong()
        is Double -> {
            val isWholeInULongRange = value % 1.0 == 0.0 && value >= 0.0 && value < ULong.MAX_VALUE.toDouble()
            if (isWholeInULongRange) value.toULong() else null
        }
        else -> null
    } ?: throwError("${EM.ERR_EXPECTED_ULONG_PREFIX}$value")
    pathTracker.finishScalarValue()
    return unsignedNumber
}

internal fun GhostYamlFlatReader.nextULongOrNullImpl(): ULong? = nextOrNullImpl { nextULong() }

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextDoubleImpl(): Double {
    val value = takeNextValue()
    val number = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    } ?: throwError("${EM.ERR_EXPECTED_DOUBLE_PREFIX}$value")
    pathTracker.finishScalarValue()
    return number
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextFloatImpl(): Float {
    val value = takeNextValue()
    val number = when (value) {
        is Long -> value.toFloat()
        is Number -> value.toDouble().toFloat().takeIf { it.isFinite() || !value.toDouble().isFinite() }
        is String -> exactFloatOrNull(text = value)
        else -> null
    } ?: throwError("${EM.ERR_EXPECTED_FLOAT_PREFIX}$value")
    pathTracker.finishScalarValue()
    return number
}

/**
 * A YAML integer scalar, a numeric string, or a float scalar with no fractional part (`1e2`,
 * `1.0`) as a [Long]; `null` for anything else, including a fraction or a value outside [Long],
 * so callers throw instead of truncating or wrapping it.
 */
internal fun wholeNumberOrNull(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is String -> value.toLongOrNull()
    is Double -> {
        val isWholeInLongRange = value % 1.0 == 0.0 &&
            value >= Long.MIN_VALUE.toDouble() &&
            value < -Long.MIN_VALUE.toDouble()
        if (isWholeInLongRange) value.toLong() else null
    }
    else -> null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextBooleanImpl(): Boolean {
    val value = takeNextValue()
    if (value is Boolean) {
        pathTracker.finishScalarValue()
        return value
    }
    if (value is String) {
        val parsed = if (coerceBooleans) {
            matchCoerceBooleanBytes(
                start = 0,
                length = value.length,
                onError = { throwError("${EM.ERR_EXPECTED_BOOLEAN_PREFIX}$value") },
                getByte = { index -> value[index].code }
            )
        } else {
            value.lowercase().toBooleanStrictOrNull()
                ?: throwError("${EM.ERR_EXPECTED_BOOLEAN_PREFIX}$value")
        }
        pathTracker.finishScalarValue()
        return parsed
    }
    throwError("${EM.ERR_EXPECTED_BOOLEAN_PREFIX}$value")
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextCharImpl(): Char {
    val value = takeNextValue()
    val text = if (value == null) "" else value.toString()
    if (text.length != 1) {
        throwError("${EM.ERR_EXPECTED_SINGLE_CHAR_LEN_PREFIX}${text.length}")
    }
    pathTracker.finishScalarValue()
    return text[0]
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextStringImpl(): String {
    val value = takeNextValue()
    val isScalar = value != null && value !is Map<*, *> && value !is List<*>
    if (!isScalar) {
        throwError("${EM.ERR_EXPECTED_STRING_PREFIX}$value")
    }
    pathTracker.finishScalarValue()
    return value.toString()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.beginArrayImpl() {
    ensureRootParsed()
    val list = nextValue as? List<*>
        ?: throwError("${EM.ERR_EXPECTED_LIST_PREFIX}$nextValue")

    pathTracker.pushArray()
    pushTraversalFrame()

    currentList = list
    listIterator = list.iterator()
    currentMap = null
    mapIterator = null
    currentEntry = null
    nextValue = null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.endArrayImpl() {
    popTraversalFrameOrClear()
    pathTracker.finishArrayValue()
}

/** Pushes the current map/list traversal state, shared by [beginObjectImpl] and [beginArrayImpl]. */
private fun GhostYamlFlatReader.pushTraversalFrame() {
    traversalStack.add(
        GhostYamlFlatReader.StateFrame(
            currentMap,
            mapIterator,
            currentEntry,
            currentList,
            listIterator
        )
    )
}

/**
 * Restores the enclosing map/list traversal state (or clears it at the root), shared by
 * [endObjectImpl] and [endArrayImpl].
 */
private fun GhostYamlFlatReader.popTraversalFrameOrClear() {
    if (traversalStack.isNotEmpty()) {
        val frame = traversalStack.removeAt(traversalStack.size - 1)
        currentMap = frame.map
        mapIterator = frame.mapIterator
        currentEntry = frame.entry
        currentList = frame.list
        listIterator = frame.listIterator
    } else {
        currentMap = null
        mapIterator = null
        currentEntry = null
        currentList = null
        listIterator = null
    }
    nextValue = null
}

internal fun GhostYamlFlatReader.hasNextImpl(): Boolean {
    return mapIterator?.hasNext() == true
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.hasNextArrayElementImpl(): Boolean {
    val iterator = listIterator ?: return false
    if (iterator.hasNext()) {
        pathTracker.enterArrayElement()
        nextValue = iterator.next()
        return true
    }
    return false
}

internal fun GhostYamlFlatReader.isNextCloseArrayImpl(): Boolean {
    val iterator = listIterator
    return iterator == null || !iterator.hasNext()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextKeyImpl(): String? {
    val iterator = mapIterator ?: return null
    if (iterator.hasNext()) {
        val entry = iterator.next()
        currentEntry = entry
        nextValue = entry.value
        pathTracker.pushKey(name = entry.key)
        return entry.key
    }
    return null
}

internal fun GhostYamlFlatReader.consumeKeySeparatorImpl() {
    // No-op for AST traversal
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.throwErrorImpl(message: String): Nothing {
    throw GhostYamlException(
        baseMessage = message,
        path = pathTracker.formatPath(),
        hint = message.hintForYamlError(),
    )
}

internal fun GhostYamlFlatReader.peekStringFieldImpl(name: String): String? {
    ensureRootParsed()
    val currentObj = nextValue as? Map<*, *> ?: return null
    return currentObj[name]?.toString()
}
