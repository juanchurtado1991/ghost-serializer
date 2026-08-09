package com.ghost.serialization.parser.yaml

import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.yaml.exception.GhostYamlException
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

internal fun GhostYamlFlatReader.ensureRootParsed() {
    if (!rootParsed) {
        rootObject = readDocument()
        nextValue = rootObject
        rootParsed = true
    }
}

/** Called once per document by [GhostYamlFlatReader.readAllDocuments] (typed overload). */
internal fun GhostYamlFlatReader.prepareRootForCurrentDocument() {
    traversalStack.clear()
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
internal fun GhostYamlFlatReader.clearAfterDocument() {
    traversalStack.clear()
    currentMap = null
    mapIterator = null
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
    rootParsed = false
    rootObject = null
}

internal fun GhostYamlFlatReader.beginObjectImpl() {
    ensureRootParsed()
    val map =
        nextValue as? Map<*, *> ?: throw GhostYamlException("${C.ERR_EXPECTED_MAP_PREFIX}$nextValue")

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
    currentMap = map as Map<String, Any?>
    mapIterator = currentMap!!.entries.iterator()
    currentEntry = null
    currentList = null
    listIterator = null
    nextValue = null
}

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
}

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

internal fun GhostYamlFlatReader.skipValueImpl() {
    nextValue = null
}

internal fun GhostYamlFlatReader.isNextNullValueImpl(): Boolean {
    ensureRootParsed()
    return nextValue == null
}

internal fun GhostYamlFlatReader.consumeNullImpl() {
    nextValue = null
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

internal fun GhostYamlFlatReader.nextIntImpl(): Int {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        return value.toInt()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            return value.toIntOrNull() ?: 0
        }
        return value.toInt()
    }
    throw GhostYamlException("${C.ERR_EXPECTED_INT_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextLongImpl(): Long {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        return value.toLong()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            return value.toLongOrNull() ?: 0L
        }
        return value.toLong()
    }
    throw GhostYamlException("${C.ERR_EXPECTED_LONG_PREFIX}$value")
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
internal fun GhostYamlFlatReader.nextULongImpl(): ULong {
    val value = nextValue
    nextValue = null
    when (value) {
        is Number -> return value.toLong().toULong()
        is String -> {
            if (coerceStringsToNumbers) {
                return value.toULongOrNull() ?: 0uL
            }
            return value.toULong()
        }
    }
    throw GhostYamlException("${C.ERR_EXPECTED_ULONG_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextULongOrNullImpl(): ULong? {
    if (isNextNullValue()) {
        consumeNull()
        return null
    }
    return nextULong()
}

internal fun GhostYamlFlatReader.nextDoubleImpl(): Double {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        return value.toDouble()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            return value.toDoubleOrNull() ?: 0.0
        }
        return value.toDouble()
    }
    throw GhostYamlException("${C.ERR_EXPECTED_DOUBLE_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextFloatImpl(): Float {
    val value = nextValue
    nextValue = null
    if (value is Number) {
        return value.toFloat()
    }
    if (value is String) {
        if (coerceStringsToNumbers) {
            return value.toFloatOrNull() ?: 0.0f
        }
        return value.toFloat()
    }
    throw GhostYamlException("${C.ERR_EXPECTED_FLOAT_PREFIX}$value")
}

internal fun GhostYamlFlatReader.nextBooleanImpl(): Boolean {
    val value = nextValue
    nextValue = null
    if (value is Boolean) {
        return value
    }
    if (value is String) {
        if (coerceBooleans) {
            return value.lowercase() == C.STR_TRUE
        }
        return value.toBoolean()
    }
    throw GhostYamlException("${C.ERR_EXPECTED_BOOLEAN_PREFIX}$value")
}

/** Reads a YAML scalar that must decode to exactly one UTF-16 [Char]. */
internal fun GhostYamlFlatReader.nextCharImpl(): Char {
    val text = nextString()
    if (text.length != 1) {
        throw GhostYamlException("${C.ERR_EXPECTED_SINGLE_CHAR_LEN_PREFIX}${text.length}")
    }
    return text[0]
}

internal fun GhostYamlFlatReader.nextStringImpl(): String {
    val value = nextValue
    nextValue = null
    if (value == null) return ""
    return value.toString()
}

internal fun GhostYamlFlatReader.beginArrayImpl() {
    ensureRootParsed()
    val list = nextValue as? List<*>
        ?: throw GhostYamlException("${C.ERR_EXPECTED_LIST_PREFIX}$nextValue")

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
    listIterator = currentList!!.iterator()
    currentMap = null
    mapIterator = null
    currentEntry = null
    nextValue = null
}

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
}

internal fun GhostYamlFlatReader.hasNextImpl(): Boolean {
    return mapIterator?.hasNext() == true
}

internal fun GhostYamlFlatReader.hasNextArrayElementImpl(): Boolean {
    val iterator = listIterator ?: return false
    if (iterator.hasNext()) {
        nextValue = iterator.next()
        return true
    }
    return false
}

internal fun GhostYamlFlatReader.isNextCloseArrayImpl(): Boolean {
    return listIterator == null || !listIterator!!.hasNext()
}

internal fun GhostYamlFlatReader.nextKeyImpl(): String? {
    val iterator = mapIterator ?: return null
    if (iterator.hasNext()) {
        val entry = iterator.next()
        currentEntry = entry
        nextValue = entry.value
        return entry.key
    }
    return null
}

internal fun GhostYamlFlatReader.consumeKeySeparatorImpl() {
    // No-op for AST traversal
}

internal fun GhostYamlFlatReader.throwErrorImpl(message: String): Nothing {
    throw GhostYamlException(message)
}

internal fun GhostYamlFlatReader.peekStringFieldImpl(name: String): String? {
    ensureRootParsed()
    val currentObj = nextValue as? Map<*, *> ?: return null
    return currentObj[name]?.toString()
}
