package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.json.GhostJsonPathTracker
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC

/**
 * High-performance YAML reader operating on a [ByteArray] with minimal intermediate allocations.
 *
 * Compares bytes directly (avoids `.toChar()` on hot paths); control bytes live in
 * `GhostYamlTokens`; digit/whitespace/alpha checks use bitwise ops; field matching operates
 * on raw bytes with string decoding deferred until final value extraction.
 *
 * @property position Current read position in [rawData].
 * @property limit Exclusive upper bound — parse only up to this index.
 * @property currentIndent Current indentation column (0-based). Updated on every line.
 * @property indentHasTab Whether a tab appears in the current line's leading whitespace after
 *   [currentIndent] spaces. Tabs have no fixed column width, so YAML forbids them in indentation
 *   that opens/extends a block mapping/sequence, but they're harmless once content has started.
 * @property depth Guards against stack overflow on extreme nesting.
 * @property anchorTable Anchors defined in the current document.
 * @property tagDirectives Tag directives defined in the current document.
 * @property pathTracker Cursor-phase JSONPath breadcrumbs (same tracker as JSON). Format only on
 *   throw. Parse-phase [yamlError] does not use this stack (path stays `"$"`).
 *
 * Every `xxx()` member below (`beginObject`, `nextInt`, etc.) is a thin delegate to an
 * identically-named `xxxImpl` extension function in `GhostYamlCursorTraversal.kt` — they stay
 * real members here (public API consumed by KSP-generated code in other Gradle modules) instead
 * of becoming extension functions the way the block/flow/tag/anchor subsystems below do.
 * [isDigit] is a bitwise check (no `.toChar()`, no range object allocation), kept here since it's
 * already internal and shared with `GhostYamlBlockScalarSubsystem.kt`.
 */
@OptIn(InternalGhostApi::class)
open class GhostYamlFlatReader(var rawData: ByteArray) {

    var position: Int = 0
    var limit: Int = rawData.size
    internal var currentIndent: Int = 0
    internal var indentHasTab: Boolean = false
    internal var depth: Int = 0
    internal val anchorTable = HashMap<String, Any?>()
    internal val tagDirectives = HashMap<String, String>()

    internal var rootParsed = false
    internal var rootObject: Any? = null

    // Stack for object/list traversal (stores state elements)
    @PublishedApi
    internal val traversalStack = ArrayList<StateFrame>()

    @PublishedApi
    internal var currentMap: Map<String, Any?>? = null

    @PublishedApi
    internal var mapIterator: Iterator<Map.Entry<String, Any?>>? = null

    @PublishedApi
    internal var currentEntry: Map.Entry<String, Any?>? = null

    @PublishedApi
    internal var currentList: List<Any?>? = null

    @PublishedApi
    internal var listIterator: Iterator<Any?>? = null

    var nextValue: Any? = null

    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()

    var strictMode: Boolean = false
    var coerceStringsToNumbers: Boolean = false
    var coerceBooleans: Boolean = false
    var maxDepth: Int = SC.MAX_DEPTH
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize

    @PublishedApi
    internal val tokenEndObject = -1

    @PublishedApi
    internal val tokenUnknownName = -2

    // Reset/document/value/key reading now live in GhostYamlDocumentReading.kt and
    // GhostYamlValueReading.kt (extension functions: reset, readDocument, readAllDocuments,
    // readValue, readPlainScalarOrMapping, readKey, etc.) — same package, same pattern as the
    // anchor/tag/flow-style/block-scalar subsystems below.

    // Scalar interpretation, quoted-string unescaping, and number parsing now live in
    // GhostYamlScalarDecoding.kt (extension functions: interpretScalar, readDoubleQuotedString,
    // readSingleQuotedString, readNumber, etc.) — same package, same pattern as the anchor/tag/
    // flow-style/block-scalar subsystems below.

    // ── Scalar subsystems (block, flow, tags, anchors) ─────────────────────────

    // Whitespace/comment/indentation/document-marker handling now lives in
    // GhostYamlWhitespace.kt (skipWhitespaceAndComments, advanceLine, isDocumentMarker, etc.) —
    // same package, same extension-function pattern as the other subsystems.

    // ── Bitwise scalar type checks ─────────────────────────────────────────────
    // isNullLiteral/isTrueLiteral/isFalseLiteral/tryParseNumber moved to
    // GhostYamlScalarDecoding.kt alongside interpretScalar, their only caller. isDigit stays here
    // — already internal and shared with GhostYamlBlockScalarSubsystem.kt.

    internal fun isDigit(currentByte: Byte): Boolean =
        (currentByte - SC.DIGIT_LOWER_BOUND).toUByte() <= (SC.DIGIT_UPPER_BOUND - SC.DIGIT_LOWER_BOUND).toUByte()

    // Error reporting (yamlError) now lives in GhostYamlErrorReporting.kt; document-level error
    // helpers (noProgressError, rejectTrailingGarbageAfterDocument,
    // requireExplicitEndBeforeDirectives) moved alongside readDocument/readAllDocuments in
    // GhostYamlDocumentReading.kt.

    fun beginObject() = beginObjectImpl()
    fun endObject() = endObjectImpl()
    fun selectNameAndConsume(options: JsonReaderOptions): Int = selectNameAndConsumeImpl(options = options)
    fun selectString(options: JsonReaderOptions): Int = selectStringImpl(options = options)
    fun skipValue() = skipValueImpl()
    fun isNextNullValue(): Boolean = isNextNullValueImpl()
    fun consumeNull() = consumeNullImpl()

    // The nextXxxOrNull() family below returns null specifically when the next value is the YAML
    // null literal (~/null/Null/NULL), not on any other kind of failure.
    fun nextStringOrNull(): String? = nextStringOrNullImpl()
    fun nextIntOrNull(): Int? = nextIntOrNullImpl()
    open fun nextLongOrNull(): Long? = nextLongOrNullImpl()
    fun nextBooleanOrNull(): Boolean? = nextBooleanOrNullImpl()

    fun nextInt(): Int = nextIntImpl()
    open fun nextLong(): Long = nextLongImpl()
    open fun nextProtoUInt64(): ULong = nextProtoUInt64Impl()

    /** Plain YAML scalar `ULong` — accepts numeric or string scalars (full range via decimal string). */
    open fun nextULong(): ULong = nextULongImpl()
    open fun nextULongOrNull(): ULong? = nextULongOrNullImpl()
    fun nextDouble(): Double = nextDoubleImpl()
    fun nextFloat(): Float = nextFloatImpl()
    fun nextBoolean(): Boolean = nextBooleanImpl()

    /** Reads a YAML scalar that must decode to exactly one UTF-16 [Char]. */
    fun nextChar(): Char = nextCharImpl()

    fun nextString(): String = nextStringImpl()
    fun beginArray() = beginArrayImpl()
    fun endArray() = endArrayImpl()
    fun hasNext(): Boolean = hasNextImpl()
    fun hasNextArrayElement(): Boolean = hasNextArrayElementImpl()
    fun isNextCloseArray(): Boolean = isNextCloseArrayImpl()
    fun nextKey(): String? = nextKeyImpl()
    fun consumeKeySeparator() = consumeKeySeparatorImpl()
    fun throwError(message: String): Nothing = throwErrorImpl(message = message)

    /**
     * Throws for a missing required field, pushing [jsonName] onto the JSONPath so the
     * exception points at `$.….<jsonName>` (validation runs before [endObject]).
     */
    fun throwMissingRequiredField(jsonName: String): Nothing {
        pathTracker.pushKey(name = jsonName)
        throwError("${EM.ERR_REQUIRED_FIELD_PREFIX}$jsonName${EM.ERR_REQUIRED_FIELD_SUFFIX}")
    }

    fun peekStringField(name: String): String? = peekStringFieldImpl(name = name)

    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        beginArray()
        if (isNextCloseArray()) {
            endArray()
            return emptyList()
        }
        val list = ArrayList<T>()
        while (hasNextArrayElement()) {
            list.add(itemParser())
        }
        endArray()
        return list
    }

    inline fun <T> readSet(crossinline itemParser: () -> T): Set<T> {
        beginArray()
        if (isNextCloseArray()) {
            endArray()
            return emptySet()
        }
        val set = LinkedHashSet<T>()
        while (hasNextArrayElement()) {
            set.add(itemParser())
        }
        endArray()
        return set
    }

    inline fun <K, V> readMap(
        crossinline keyParser: () -> K,
        crossinline valueParser: () -> V
    ): Map<K, V> {
        beginObject()
        val map = HashMap<K, V>()
        while (hasNext()) {
            val key = keyParser()
            consumeKeySeparator()
            val value = valueParser()
            map[key] = value
        }
        endObject()
        return map
    }

    // ── Chomp style enum ──────────────────────────────────────────────────────

    internal enum class ChompStyle { STRIP, CLIP, KEEP }

    // ── JSON stream-compatible cursor traversal APIs ──────────────────────────
    @PublishedApi
    internal class StateFrame(
        val map: Map<String, Any?>?,
        val mapIterator: Iterator<Map.Entry<String, Any?>>?,
        val entry: Map.Entry<String, Any?>?,
        val list: List<Any?>?,
        val listIterator: Iterator<Any?>?
    )
}
