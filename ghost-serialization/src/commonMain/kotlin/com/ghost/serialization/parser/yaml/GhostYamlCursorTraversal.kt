package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.exception.hintForYamlError
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Implementation behind [GhostYamlFlatReader]'s `JsonReader`-compatible cursor traversal API
 * (`beginObject`/`endObject`/`nextString`/`nextInt`/etc.) — a second-phase facade that walks the
 * already-fully-parsed in-memory `Map`/`List` AST built by [GhostYamlFlatReader.readDocument] via
 * plain iterators. Zero byte-level scanning happens here, and nothing here calls back into the
 * byte-level parser (`readValue`, `readBlockMapping`, ...).
 *
 * Every method in [GhostYamlFlatReader] itself stays a thin one-line delegate to the identically-
 * named `xxxImpl` function here — deliberately, not following the plain extension-function pattern
 * the other subsystems use: `beginObject`/`nextString`/etc. are public members called by
 * KSP-generated `deserialize()` bodies that may live in a *different Gradle module's package*
 * (see `ghost-compiler`'s `GhostEmitterConstants`), and Kotlin resolves true class members via the
 * receiver type with zero imports — converting them to extension functions directly would require
 * every downstream consumer's generated code to gain a new import the compiler doesn't emit today.
 * The state fields these functions manipulate (`traversalStack`, `currentMap`, `nextValue`, etc.)
 * necessarily stay declared on `GhostYamlFlatReader` itself — Kotlin classes can't span files, and
 * `readList`/`readSet`/`readMap` are `public inline fun` whose `@PublishedApi internal` field
 * access has to resolve against wherever the class body lives.
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
    traversalStack.clear()
    pathTracker.reset()
    currentMap = null
    mapIterator = null
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
    rootObject = readValue(indent = C.INDENT_UNSET, inFlow = false)
    nextValue = rootObject
    rootParsed = true
}

/** Called once per document by [GhostYamlFlatReader.readAllDocuments] (typed overload). */
@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.clearAfterDocument() {
    traversalStack.clear()
    pathTracker.reset()
    currentMap = null
    mapIterator = null
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
    rootParsed = false
    rootObject = null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.beginObjectImpl() {
    ensureRootParsed()
    val map = nextValue as? Map<*, *>
        ?: throwError("${C.ERR_EXPECTED_MAP_PREFIX}$nextValue")

    pathTracker.pushObject()
    traversalStack.add(
        GhostYamlFlatReader.StateFrame(
            currentMap,
            mapIterator,
            currentEntry,
            currentList,
            listIterator
        )
    )

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

    val index = options.findOptionIndex(entry.key)
    if (index >= 0) {
        pathTracker.pushKey(entry.key)
        return index
    }
    return tokenUnknownName
}

internal fun GhostYamlFlatReader.selectStringImpl(options: JsonReaderOptions): Int {
    val strValue = nextString()
    val index = options.findOptionIndex(strValue)
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

/** Reads a YAML string, or `null` when the next value is YAML null. */
internal fun GhostYamlFlatReader.nextStringOrNullImpl(): String? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextString()
}

/** Reads a YAML int, or `null` when the next value is YAML null. */
internal fun GhostYamlFlatReader.nextIntOrNullImpl(): Int? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextInt()
}

/** Reads a YAML long, or `null` when the next value is YAML null. */
internal fun GhostYamlFlatReader.nextLongOrNullImpl(): Long? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextLong()
}

/** Reads a YAML boolean, or `null` when the next value is YAML null. */
internal fun GhostYamlFlatReader.nextBooleanOrNullImpl(): Boolean? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextBoolean()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextIntImpl(): Int {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        pathTracker.finishScalarValue()
        return value.toInt()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            pathTracker.finishScalarValue()
            return value.toIntOrNull() ?: 0
        }
        val parsed = value.toIntOrNull()
            ?: throwError("${C.ERR_EXPECTED_INT_PREFIX}$value")
        pathTracker.finishScalarValue()
        return parsed
    }
    throwError("${C.ERR_EXPECTED_INT_PREFIX}$value")
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextLongImpl(): Long {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        pathTracker.finishScalarValue()
        return value.toLong()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            pathTracker.finishScalarValue()
            return value.toLongOrNull() ?: 0L
        }
        val parsed = value.toLongOrNull()
            ?: throwError("${C.ERR_EXPECTED_LONG_PREFIX}$value")
        pathTracker.finishScalarValue()
        return parsed
    }
    throwError("${C.ERR_EXPECTED_LONG_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextProtoUInt64Impl(): ULong {
    val previous = coerceStringsToNumbers
    coerceStringsToNumbers = true
    return try {
        nextString().toULong()
    } finally {
        coerceStringsToNumbers = previous
    }
}

/** Plain YAML scalar `ULong` — accepts numeric or string scalars (full range via decimal string). */
@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextULongImpl(): ULong {
    val value = nextValue
    nextValue = null
    when (value) {
        is Number -> {
            pathTracker.finishScalarValue()
            return value.toLong().toULong()
        }
        is String -> {
            if (coerceStringsToNumbers) {
                pathTracker.finishScalarValue()
                return value.toULongOrNull() ?: 0uL
            }
            val parsed = value.toULongOrNull()
                ?: throwError("${C.ERR_EXPECTED_ULONG_PREFIX}$value")
            pathTracker.finishScalarValue()
            return parsed
        }
    }
    throwError("${C.ERR_EXPECTED_ULONG_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextULongOrNullImpl(): ULong? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextULong()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextDoubleImpl(): Double {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        pathTracker.finishScalarValue()
        return value.toDouble()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            pathTracker.finishScalarValue()
            return value.toDoubleOrNull() ?: 0.0
        }
        val parsed = value.toDoubleOrNull()
            ?: throwError("${C.ERR_EXPECTED_DOUBLE_PREFIX}$value")
        pathTracker.finishScalarValue()
        return parsed
    }
    throwError("${C.ERR_EXPECTED_DOUBLE_PREFIX}$value")
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextFloatImpl(): Float {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        pathTracker.finishScalarValue()
        return value.toFloat()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            pathTracker.finishScalarValue()
            return value.toFloatOrNull() ?: 0.0f
        }
        val parsed = value.toFloatOrNull()
            ?: throwError("${C.ERR_EXPECTED_FLOAT_PREFIX}$value")
        pathTracker.finishScalarValue()
        return parsed
    }
    throwError("${C.ERR_EXPECTED_FLOAT_PREFIX}$value")
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextBooleanImpl(): Boolean {
    val value = nextValue
    nextValue = null
    if (value is Boolean) {
        pathTracker.finishScalarValue()
        return value
    }
    if (value is String) {
        if (coerceBooleans) {
            pathTracker.finishScalarValue()
            return value.lowercase() == C.STR_TRUE
        }
        pathTracker.finishScalarValue()
        return value.toBoolean()
    }
    throwError("${C.ERR_EXPECTED_BOOLEAN_PREFIX}$value")
}

/** Reads a YAML scalar that must decode to exactly one UTF-16 [Char]. */
@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextCharImpl(): Char {
    val value = nextValue
    nextValue = null
    val text = if (value == null) "" else value.toString()
    if (text.length != 1) {
        throwError("${C.ERR_EXPECTED_SINGLE_CHAR_LEN_PREFIX}${text.length}")
    }
    pathTracker.finishScalarValue()
    return text[0]
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.nextStringImpl(): String {
    val value = nextValue
    nextValue = null
    pathTracker.finishScalarValue()
    if (value == null) return ""
    return value.toString()
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.beginArrayImpl() {
    ensureRootParsed()
    val list = nextValue as? List<*>
        ?: throwError("${C.ERR_EXPECTED_LIST_PREFIX}$nextValue")

    pathTracker.pushArray()
    traversalStack.add(
        GhostYamlFlatReader.StateFrame(
            currentMap,
            mapIterator,
            currentEntry,
            currentList,
            listIterator
        )
    )

    currentList = list
    listIterator = list.iterator()
    currentMap = null
    mapIterator = null
    currentEntry = null
    nextValue = null
}

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.endArrayImpl() {
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
    pathTracker.finishArrayValue()
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
        pathTracker.pushKey(entry.key)
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
        hint = hintForYamlError(message),
    )
}

internal fun GhostYamlFlatReader.peekStringFieldImpl(name: String): String? {
    ensureRootParsed()
    val currentObj = nextValue as? Map<*, *> ?: return null
    return currentObj[name]?.toString()
}
