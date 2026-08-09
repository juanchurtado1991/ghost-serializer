package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.GhostYamlConstants as C


/**
 * High-performance YAML reader operating on a [ByteArray] with minimal intermediate allocations.
 *
 * ## Design
 * - Compare bytes directly; avoid `.toChar()` in performance-sensitive paths.
 * - All control bytes are defined in `GhostYamlConstants`.
 * - Validations use bitwise operations for digits, whitespace, and alphabetic characters.
 * - Field matching operates on raw bytes; string decoding is deferred until final value extraction.
 * - Extension hooks for block scalars, flow style, tags, and anchors are wired at construction time.
 *
 * @param rawData The full YAML document as a UTF-8 [ByteArray].
 */
@OptIn(InternalGhostApi::class)
open class GhostYamlFlatReader(var rawData: ByteArray) {

    /** Current read position in [rawData]. */
    var position: Int = 0

    /** Exclusive upper bound — parse only up to this index. */
    var limit: Int = rawData.size

    /** Current indentation column (0-based). Updated on every line. */
    internal var currentIndent: Int = 0

    /**
     * Whether a tab byte appears in the current line's leading whitespace, between the counted
     * [currentIndent] spaces and the first non-whitespace byte. Tabs have no fixed column width,
     * so the YAML spec forbids them in the indentation used to open or extend a block mapping/
     * sequence — but they're harmless as ordinary whitespace once a scalar's own content has
     * started. See the [currentIndent]-consuming checks in `readBlockMapping`/`readBlockSequence`.
     */
    internal var indentHasTab: Boolean = false

    /** Depth counter — guards against stack overflow on extreme nesting. */
    internal var depth: Int = 0

    /** Table of defined anchors for the current document. */
    internal val anchorTable = HashMap<String, Any?>()

    /** Table of defined tag directives for the current document. */
    internal val tagDirectives = HashMap<String, String>()

    /**
     * Resets the reader's state to process a new byte payload.
     */
    fun reset(newData: ByteArray) {
        rawData = newData
        position = 0
        limit = newData.size
        currentIndent = 0
        depth = 0
        anchorTable.clear()
        tagDirectives.clear()
        rootParsed = false
        rootObject = null
        traversalStack.clear()
        currentMap = null
        mapIterator = null
        currentEntry = null
        currentList = null
        listIterator = null
        nextValue = null
        strictMode = false
        coerceStringsToNumbers = false
        coerceBooleans = false
        maxDepth = C.MAX_DEPTH
        maxCollectionSize = GhostHeuristics.maxCollectionSize
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Reads a single YAML document from the current position.
     * Returns the parsed value: Map, List, String, Long, Double, Boolean, or null.
     */
    fun readDocument(): Any? {
        anchorTable.clear()
        tagDirectives.clear()
        skipDirectivesAndDocumentStart()
        skipWhitespaceAndComments()
        if (position >= limit) return emptyMap<String, Any?>()
        return readValue(indent = C.INDENT_UNSET, inFlow = false)
    }

    /**
     * Reads all YAML documents in the source (separated by `---`).
     */
    fun readAllDocuments(): List<Any?> {
        val results = mutableListOf<Any?>()
        val localLimit = limit
        var previousDocumentExplicitlyEnded = true
        while (position < localLimit) {
            anchorTable.clear()
            tagDirectives.clear()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            requireExplicitEndBeforeDirectives(previousDocumentExplicitlyEnded)
            val iterationStart = position
            val sawExplicitMarker = skipDirectivesAndDocumentStart()
            skipWhitespaceAndComments()
            if (!sawExplicitMarker && position >= localLimit) break
            // A bare `...` with no preceding `---` isn't an empty document — it's the end
            // marker for a document that was never started (e.g. after a comment, or another
            // `...`). Consume it and loop rather than reading it as a null-valued document.
            if (!sawExplicitMarker && isDocumentEndMarker()) {
                skipDocumentEnd()
                previousDocumentExplicitlyEnded = true
                continue
            }
            results.add(readValue(indent = C.INDENT_UNSET, inFlow = false))
            val sawExplicitEnd = skipDocumentEnd()
            if (!sawExplicitEnd) rejectTrailingGarbageAfterDocument()
            previousDocumentExplicitlyEnded = sawExplicitEnd
            if (position == iterationStart) noProgressError()
        }
        return results
    }

    /**
     * Reads every YAML document and deserializes each with [deserializeDocument].
     * Resets traversal state between documents so generated `GhostYamlSerializer` paths work.
     */
    fun <T> readAllDocuments(deserializeDocument: (GhostYamlFlatReader) -> T): List<T> {
        val results = mutableListOf<T>()
        val localLimit = limit
        var previousDocumentExplicitlyEnded = true
        while (position < localLimit) {
            anchorTable.clear()
            tagDirectives.clear()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            requireExplicitEndBeforeDirectives(previousDocumentExplicitlyEnded)
            val iterationStart = position
            val sawExplicitMarker = skipDirectivesAndDocumentStart()
            skipWhitespaceAndComments()
            if (!sawExplicitMarker && position >= localLimit) break
            if (!sawExplicitMarker && isDocumentEndMarker()) {
                skipDocumentEnd()
                previousDocumentExplicitlyEnded = true
                continue
            }
            prepareRootForCurrentDocument()
            results.add(deserializeDocument(this))
            clearAfterDocument()
            val sawExplicitEnd = skipDocumentEnd()
            if (!sawExplicitEnd) rejectTrailingGarbageAfterDocument()
            previousDocumentExplicitlyEnded = sawExplicitEnd
            if (position == iterationStart) noProgressError()
        }
        return results
    }

    // ── Core state machine ─────────────────────────────────────────────────────

    /**
     * Reads the next YAML value at the current position.
     *
     * @param indent The indentation level of the enclosing context (INDENT_UNSET for root).
     * @param inFlow Whether we are inside a flow collection `{...}` or `[...]`.
     */
    internal fun readValue(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE,
        strictDedent: Boolean = false,
        allowMappingRedirect: Boolean = true,
        // Distinct from `indent`: if this value turns out to be a plain scalar, later lines only
        // need to be indented more than *this* to keep folding into it — normally the same as
        // `indent`, since a value read inline (same line as its key's ':') and an already-known
        // nested-collection's own indent boundary coincide. The one case they diverge is a
        // mapping value that starts on its own line (resolveValueAfterColon's newline branch):
        // `indent` there is the *auto-detected column of this specific value* (needed unchanged
        // if it turns out to be a nested block collection instead), but a plain scalar's fold
        // boundary per the YAML spec is the *enclosing mapping's* own indent, not the value's.
        foldIndent: Int = indent
    ): Any? {
        skipInlineWhitespace()
        val localLimit = limit
        if (position >= localLimit) {
            // A tag forcing string type (e.g. a bare "!!str" with nothing after it, even at
            // end of input) still resolves to an empty string, not "no value at all" — same as
            // it would if there were trailing whitespace/newline instead of EOF.
            return if (expectedTag == GhostYamlTags.TAG_STR) "" else null
        }

        val currentByte = rawData[position]
        return when (currentByte) {
            C.PIPE_BYTE, C.GT_BYTE -> readBlockScalar(currentByte, indent)           // block scalar
            C.LEFT_BRACE_BYTE -> readFlowCollectionOrMappingKey(indent, inFlow) { readFlowMapping() }
            C.LEFT_BRACKET_BYTE -> readFlowCollectionOrMappingKey(indent, inFlow) { readFlowSequence() }
            C.EXCLAMATION_BYTE -> readTaggedValue(indent, inFlow)      // tagged value
            C.AMPERSAND_BYTE -> readAnchoredValueOrMappingKey(indent, inFlow, strictDedent)    // anchor definition
            C.ASTERISK_BYTE -> readAliasOrMappingKey(indent, inFlow)                  // alias reference
            C.DOUBLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent, inFlow) { readDoubleQuotedString() }
            C.SINGLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent, inFlow) { readSingleQuotedString() }
            C.DOT_BYTE -> if (isDocumentEndMarker()) null else readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
            // '%' is reserved for directives and can never start a plain scalar (no "followed by
            // a safe character" exception the way '-'/'?'/':' get) — a directive-shaped line
            // appearing where a value is expected (e.g. after the "---" it should have preceded)
            // is simply invalid, not a scalar that happens to start with '%'.
            C.PERCENT_BYTE -> yamlError(C.ERR_PLAIN_SCALAR_PERCENT)
            C.QUESTION_BYTE ->
                if (!inFlow && isExplicitKeyIndicator()) readBlockMapping(indent.coerceAtLeast(0))
                else readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
            C.DASH_BYTE -> {
                // Either: negative number "-42", block sequence "- item", or doc separator "---"
                val nextByte = if (position + 1 < localLimit) rawData[position + 1] else 0
                when {
                    expectedTag != GhostYamlTags.TAG_STR && isDigit(nextByte) -> readNumber()
                    nextByte == C.SPACE_BYTE || nextByte == C.NEWLINE_BYTE || nextByte == C.CR_BYTE ||
                        nextByte == C.TAB_BYTE || position + 1 >= localLimit ->
                        readBlockSequence(indent)

                    isDocumentMarker() -> null  // document end
                    else -> readPlainScalar(indent, inFlow, expectedTag, allowMappingRedirect)
                }
            }

            else -> readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
        }
    }

    /**
     * Reads a quoted scalar via [readQuoted], then checks whether a `:` (key separator) follows —
     * a quoted string can be a mapping key, not just a value (e.g. `"400":` in `responses:` below
     * a `- `/another key). [readPlainScalarOrMapping] already does this colon-scan for *bare* keys,
     * but readValue's dispatch never reaches it for quoted content since [C.DOUBLE_QUOTE_BYTE] /
     * [C.SINGLE_QUOTE_BYTE] are handled directly above — without this check, a quoted key's value
     * would be silently misread as the quoted string itself, dropping everything nested under it.
     */
    private inline fun readQuotedScalarOrMappingKey(indent: Int, inFlow: Boolean, readQuoted: () -> String): Any? {
        val startPosition = position
        val text = readQuoted()
        skipInlineWhitespace()
        val localLimit = limit
        val isMappingKey = position < localLimit && rawData[position] == C.COLON_BYTE &&
            (position + 1 >= localLimit ||
                rawData[position + 1] == C.SPACE_BYTE ||
                rawData[position + 1] == C.NEWLINE_BYTE ||
                rawData[position + 1] == C.CR_BYTE ||
                rawData[position + 1] == C.TAB_BYTE)
        // Inside a flow collection there's no block mapping to redirect into — leave position
        // right where readQuoted()/skipInlineWhitespace() left it (at a ':' or not) and let the
        // caller (a flow sequence entry may be an implicit single-pair mapping) decide.
        if (inFlow || !isMappingKey) return text
        position = startPosition
        return readBlockMapping(indent.coerceAtLeast(0))
    }

    /**
     * Reads a flow collection ([readCollection], `{...}` or `[...]`) via [readValue]'s dispatch,
     * then — mirroring [readQuotedScalarOrMappingKey] — checks whether a `:` follows: a flow
     * collection can itself be a block-mapping key (e.g. `[flow]: block`), not just a value.
     * Without this, `[flow]: block` at the top of a block context would read the `[flow]` list as
     * a complete document value and then choke on the trailing `: block` as garbage.
     */
    private inline fun readFlowCollectionOrMappingKey(indent: Int, inFlow: Boolean, readCollection: () -> Any?): Any? {
        val startPosition = position
        val collection = readCollection()
        if (inFlow) return collection
        skipInlineWhitespace()
        val localLimit = limit
        val isMappingKey = position < localLimit && rawData[position] == C.COLON_BYTE &&
            (position + 1 >= localLimit ||
                rawData[position + 1] == C.SPACE_BYTE ||
                rawData[position + 1] == C.NEWLINE_BYTE ||
                rawData[position + 1] == C.CR_BYTE ||
                rawData[position + 1] == C.TAB_BYTE)
        if (!isMappingKey) return collection
        position = startPosition
        return readBlockMapping(indent.coerceAtLeast(0))
    }

    // ── Block Mapping ──────────────────────────────────────────────────────────

    /**
     * Reads a block mapping starting at the current position.
     * Called when we detect "key: value" on a new line.
     *
     * @param blockIndent The indentation of the first key in this mapping.
     */
    internal fun readBlockMapping(blockIndent: Int): Map<String, Any?> {
        if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
        depth++
        val result = LinkedHashMap<String, Any?>(C.DEFAULT_MAP_CAPACITY)
        val localLimit = limit
        val localRawData = rawData
        try {
            while (position < localLimit) {
                skipWhitespaceAndComments()
                if (position >= localLimit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < blockIndent) break  // dedented — end of this mapping
                if (isDocumentMarker() || isDocumentEndMarker()) break
                // A tab can't be part of the indentation opening or extending a block mapping —
                // tabs have no fixed column width, so there's no way to compare this line's
                // indentation against blockIndent/a sibling's. Harmless once inside an already-
                // established scalar's own content (readValue never reaches this loop for that).
                if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_MAPPING_INDENT)

                if (isExplicitKeyIndicator()) {
                    val (key, value) = readExplicitKeyEntry(blockIndent)
                    result[key] = value
                    continue
                }

                // Read key
                val key = readKey(inFlow = false) ?: break
                skipInlineWhitespace()

                // Expect ':' after the key
                if (position >= localLimit || localRawData[position] != C.COLON_BYTE) {
                    yamlError("${C.ERR_EXPECTED_COLON_AFTER_KEY_PREFIX}$key${C.ERR_EXPECTED_COLON_AFTER_KEY_MID}$position")
                }
                position++ // consume ':'
                // An implicit "key: value" pair's value can't redirect into a nested block
                // mapping while still inline on the same physical line as this ':' (that shape is
                // only legal via YAML's "compact notation", reserved for explicit "?"/":" entries
                // — see resolveValueAfterColon's KDoc). Without this, "a: b: c: d" silently parsed
                // as {"a": {"b": {"c": "d"}}} instead of being rejected (yaml-test-suite ZCZ6).
                val value = resolveValueAfterColon(blockIndent, allowMappingRedirect = false)

                if (key == C.STR_MERGE_KEY) {
                    mergeInto(result, value)
                } else {
                    result[key] = value
                }
            }
        } finally {
            depth--
        }
        return result
    }

    /**
     * Called right after a mapping ':' has been consumed and inline whitespace skipped, to
     * determine and read the value. Shared by [readBlockMapping]'s implicit ("key: value")
     * entries and [readExplicitKeyEntry]'s explicit ("? key\n: value") entries — both need
     * exactly the same "same line, next line indented, or no value at all" resolution, but differ
     * on whether an *inline* value may itself redirect into a nested block mapping: explicit
     * entries get YAML's "compact notation" allowance (see the comment at
     * [readExplicitKeyEntry]'s call site), implicit ones
     * don't — [allowMappingRedirect] lets each caller opt in/out.
     */
    internal fun resolveValueAfterColon(blockIndent: Int, allowMappingRedirect: Boolean = true): Any? {
        skipInlineWhitespace()
        val localLimit = limit
        val localRawData = rawData
        if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
            skipToEndOfLine()
        }
        return when {
            position >= localLimit -> null
            localRawData[position] == C.NEWLINE_BYTE ||
                    localRawData[position] == C.CR_BYTE -> {
                advanceLine()
                skipWhitespaceAndComments()
                if (position >= localLimit) null
                else {
                    val valueIndent = currentIndent
                    if (valueIndent < blockIndent) {
                        null
                    } else if (valueIndent == blockIndent && !(localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry())) {
                        null
                    } else {
                        // foldIndent = blockIndent (not valueIndent): if this value turns out to
                        // be a plain scalar, later continuation lines only need to be indented
                        // more than the *enclosing mapping's* indent to keep folding — matching
                        // the inline-value case below — not more than this value's own
                        // auto-detected column, which is typically the SAME indent every
                        // continuation line also sits at (see 4CQQ/M5C3/NB6Z/RZT7/UGM3).
                        readValue(valueIndent, inFlow = false, foldIndent = blockIndent)
                    }
                }
            }

            else -> readValue(blockIndent, inFlow = false, strictDedent = true, allowMappingRedirect = allowMappingRedirect)
        }
    }

    // ── Block Sequence ─────────────────────────────────────────────────────────

    /**
     * Reads a block sequence (list). Each item starts with '- '.
     *
     * @param seqIndent Indentation of the '-' markers.
     */
    internal fun readBlockSequence(seqIndent: Int): List<Any?> {
        if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
        depth++
        val result = mutableListOf<Any?>()
        val localLimit = limit
        val localRawData = rawData
        try {
            while (position < localLimit) {
                skipWhitespaceAndComments()
                if (position >= localLimit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < seqIndent) break
                if (!isBlockSequenceEntry()) break
                if (isDocumentMarker()) break
                // See the equivalent check in readBlockMapping — tabs can't be part of the
                // indentation opening or extending a block sequence.
                if (indentHasTab) yamlError(C.ERR_TAB_IN_BLOCK_SEQUENCE_INDENT)

                // Consume '-'
                position++ // '-'

                // Indentation of the element value is the position of '-' plus 2.
                val elementIndent = lineIndent + 2

                // Skip the optional inline space after '-'
                if (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
                    position++
                }

                // A comment directly after "- " (e.g. "- # Empty") leaves no inline value, same
                // as it would after a mapping key's ':' (see resolveValueAfterColon) — without
                // this, the comment text would be read as the item's own plain-scalar content.
                if (position < localLimit && localRawData[position] == C.HASH_BYTE) {
                    skipToEndOfLine()
                }

                val item: Any? = when {
                    position >= localLimit -> null
                    localRawData[position] == C.NEWLINE_BYTE ||
                            localRawData[position] == C.CR_BYTE -> {
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= localLimit) null
                        else {
                            val itemIndent = currentIndent
                            if (itemIndent < elementIndent) null
                            // Delegate to readValue's own dispatch — see the equivalent comment
                            // in readBlockMapping for why (bare scalar vs. mapping vs. block
                            // scalar, not just sequence-vs-mapping).
                            else readValue(itemIndent, inFlow = false)
                        }
                    }

                    else -> {
                        // Value starts on the same line after '- '
                        // Try reading plain scalar or mapping or sequence.
                        // Since we are parsing the list item, we can call readValue with elementIndent.
                        readValue(elementIndent, inFlow = false)
                    }
                }
                result.add(item)
            }
        } finally {
            depth--
        }
        return result
    }

    // ── Plain scalar or mapping detection ─────────────────────────────────────

    /**
     * Reads either a plain scalar (string, int, float, bool, null) or detects
     * that the current content is actually a block mapping key.
     */
    internal fun readPlainScalarOrMapping(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE,
        allowMappingRedirect: Boolean = true,
        foldIndent: Int = indent
    ): Any? {
        val startPosition = position
        val localLimit = limit
        val localRawData = rawData

        // Scan forward to find ':' or end-of-line
        var scanPosition = position
        while (scanPosition < localLimit) {
            val currentByte = localRawData[scanPosition]
            when {
                currentByte == C.COLON_BYTE -> {
                    // ':' followed by space/newline/EOF → this is a mapping key
                    val afterColon = scanPosition + 1
                    if (afterColon >= localLimit ||
                        localRawData[afterColon] == C.SPACE_BYTE ||
                        localRawData[afterColon] == C.NEWLINE_BYTE ||
                        localRawData[afterColon] == C.CR_BYTE ||
                        localRawData[afterColon] == C.TAB_BYTE
                    ) {
                        if (!inFlow) {
                            if (!allowMappingRedirect) {
                                // This colon is being read as an *inline* value/key-node on the
                                // same line as an enclosing key's own ':' (e.g. the "b: c" in
                                // "a: b: c") — there's no fresh, more-indented line for a nested
                                // mapping to start on here, so this shape is just ambiguous, not
                                // a legal redirect. yaml-test-suite case ZCZ6.
                                yamlError(C.ERR_UNEXPECTED_INLINE_NESTED_MAPPING_COLON)
                            }
                            // Rewind and parse as block mapping
                            position = startPosition
                            return readBlockMapping(indent.coerceAtLeast(0))
                        }
                        // Inside a flow collection there's no block mapping to redirect into —
                        // stop the scalar right here and let the caller (a flow sequence entry
                        // may be an implicit single-pair mapping) decide what the colon means.
                        break
                    }
                    scanPosition++
                }

                currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE -> break
                currentByte == C.HASH_BYTE -> {
                    // Inline comment — the plain scalar ends before '#' (only if preceded by
                    // whitespace; "d#X" isn't a comment, "d\t#X"/"d #X" are).
                    if (scanPosition > startPosition &&
                        (localRawData[scanPosition - 1] == C.SPACE_BYTE || localRawData[scanPosition - 1] == C.TAB_BYTE)
                    ) break
                    scanPosition++
                }

                inFlow && (currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE) -> break
                else -> scanPosition++
            }
        }

        // Extract the plain scalar bytes
        val endPosition = trimTrailingSpaces(startPosition, scanPosition)
        position = scanPosition

        // A bare "-" is only a valid plain-scalar start when followed by a "safe" character
        // (ns-plain-first's rule for '-'/'?'/':') — in flow context that excludes flow indicators
        // (",[]{}"), so a lone "-" immediately touching one, like the entries in "[-, -]" or
        // "[-]", has nothing valid to be: it isn't the block sequence indicator (flow has no such
        // thing) and it doesn't satisfy the plain-scalar exception either.
        if (inFlow && endPosition - startPosition == 1 && localRawData[startPosition] == C.DASH_BYTE) {
            yamlError(C.ERR_LONE_DASH_IN_FLOW)
        }

        // Plain scalars can continue onto following lines, both in block context (more-indented
        // lines) and in flow context (any line that isn't itself a flow terminator) — see
        // [foldPlainScalarContinuation] and [foldFlowPlainScalarContinuation] respectively.
        // Line-folding rule either way: a single newline between continuation lines becomes a
        // space; a blank line (or N of them) becomes N newlines — same rule readBlockScalarContent
        // applies for folded (">") block scalars.
        if (position < localLimit &&
            (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)
        ) {
            val firstLine = localRawData.decodeToString(startPosition, endPosition)
            val folded = if (inFlow) foldFlowPlainScalarContinuation(firstLine) else foldPlainScalarContinuation(foldIndent, firstLine)
            if (folded != null) {
                val foldedBytes = folded.encodeToByteArray()
                return interpretScalar(foldedBytes, 0, foldedBytes.size, expectedTag)
            }
        }
        return interpretScalar(localRawData, startPosition, endPosition, expectedTag)
    }

    /**
     * Consumes zero or more following lines that continue a plain scalar (each more indented than
     * [indent], the enclosing block's own indentation), folding them onto [firstLine] per the
     * usual YAML line-folding rule. Returns `null` (without moving [position]) if the very next
     * line isn't actually a continuation, so the caller falls back to its single-line result.
     */
    private fun foldPlainScalarContinuation(indent: Int, firstLine: String): String? {
        val localRawData = rawData
        val localLimit = limit
        val scalarEndPosition = position
        var folded: StringBuilder? = null
        var blankLines = 0

        while (true) {
            val beforeNewline = position
            if (position < localLimit && localRawData[position] == C.CR_BYTE) position++
            if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++

            // Blank-ness is judged after skipping *all* leading whitespace (spaces and tabs) — a
            // line that's purely whitespace is blank even if that whitespace includes a tab.
            var blankScanPos = position
            while (blankScanPos < localLimit &&
                (localRawData[blankScanPos] == C.SPACE_BYTE || localRawData[blankScanPos] == C.TAB_BYTE)
            ) {
                blankScanPos++
            }
            val atBlank = blankScanPos >= localLimit ||
                localRawData[blankScanPos] == C.NEWLINE_BYTE || localRawData[blankScanPos] == C.CR_BYTE
            if (atBlank) {
                blankLines++
                position = blankScanPos
                if (position >= localLimit) break
                continue
            }

            // Not blank — how "indented" this line is (for the continuation-vs-dedent decision)
            // is judged by real spaces only, the same as everywhere else a tab can't be part of
            // establishing block-structure indentation (recomputeCurrentIndent/indentHasTab):
            // otherwise a tab sitting right at a sibling key's indentation would silently read as
            // "more indented, still a continuation" instead of surfacing as ambiguous/invalid.
            // Once a real continuation is confirmed, any tab(s) right after those spaces are still
            // ordinary leading whitespace to skip, same as the spaces themselves.
            var spaces = 0
            var peekPos = position
            while (peekPos < localLimit && localRawData[peekPos] == C.SPACE_BYTE) {
                spaces++; peekPos++
            }
            while (peekPos < localLimit && localRawData[peekPos] == C.TAB_BYTE) {
                peekPos++
            }

            position = peekPos
            if (spaces <= indent || isDocumentMarker() || isDocumentEndMarker()) {
                // Not a continuation (dedented, sibling-level, or a new document). If we'd already
                // folded at least one real continuation line, rewind just past it (trailing blank
                // lines aren't part of the value). Otherwise rewind all the way to right after the
                // first line, undoing any blank lines we tentatively scanned past too — the caller
                // must see them again to reprocess this content correctly.
                position = if (folded != null) beforeNewline else scalarEndPosition
                break
            }

            val lineStart = position
            var sawComment = false
            while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
                val currentByte = localRawData[position]
                // A comment ends this line's content the same way it ends a single-line plain
                // scalar (only when preceded by whitespace) — and since a comment can't appear
                // inside a still-open plain scalar's fold, it ends the whole scalar right here
                // too, not just this one line (see BF9H: content after the comment needs its own
                // valid token, it can't be folded in as if the comment weren't there).
                if (currentByte == C.HASH_BYTE && position > lineStart &&
                    (localRawData[position - 1] == C.SPACE_BYTE || localRawData[position - 1] == C.TAB_BYTE)
                ) {
                    sawComment = true
                    break
                }
                position++
            }
            val lineEnd = trimTrailingSpaces(lineStart, position)
            // A continuation line can't itself look like a mapping key ("word: ") — a plain
            // scalar's fold can't contain what would otherwise be a nested key/value pair (see
            // 2CMS: "invalid: x" on a continuation line is forbidden content, not literal text).
            var colonScan = lineStart
            while (colonScan < lineEnd) {
                if (localRawData[colonScan] == C.COLON_BYTE) {
                    val afterColon = colonScan + 1
                    if (afterColon >= lineEnd ||
                        localRawData[afterColon] == C.SPACE_BYTE ||
                        localRawData[afterColon] == C.TAB_BYTE
                    ) {
                        yamlError(C.ERR_PLAIN_CONTINUATION_MAPPING_KEY)
                    }
                }
                colonScan++
            }
            val lineText = localRawData.decodeToString(lineStart, lineEnd)

            if (folded == null) folded = StringBuilder(firstLine)
            if (blankLines > 0) repeat(blankLines) { folded.append('\n') } else folded.append(' ')
            folded.append(lineText)
            blankLines = 0

            // Leave position sitting right at the '#' either way — consistent with how the
            // initial single-line scan above also stops there without consuming it, letting the
            // caller's own skipWhitespaceAndComments handle it afterward.
            if (sawComment) break
            if (position >= localLimit) break
        }

        return folded?.toString()
    }

    /**
     * Flow-context counterpart to [foldPlainScalarContinuation]: folds a plain scalar's
     * continuation lines inside a flow collection (`[...]`/`{...}`). Unlike block context, a flow
     * collection isn't indentation-bounded by its surroundings once opened (it's delimited by its
     * own closing bracket/brace instead), so there's no indentation threshold to compare against
     * here — continuation is decided purely by what the next line actually starts with, using the
     * same terminators (`,`, `]`, `}`, a real `:` key separator, or a whitespace-preceded `#`
     * comment) that already end a single-line flow scalar.
     */
    private fun foldFlowPlainScalarContinuation(firstLine: String): String? {
        val localRawData = rawData
        val localLimit = limit
        val scalarEndPosition = position
        var folded: StringBuilder? = null
        var blankLines = 0

        while (true) {
            val beforeNewline = position
            if (position < localLimit && localRawData[position] == C.CR_BYTE) position++
            if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++

            var blankScanPos = position
            while (blankScanPos < localLimit &&
                (localRawData[blankScanPos] == C.SPACE_BYTE || localRawData[blankScanPos] == C.TAB_BYTE)
            ) {
                blankScanPos++
            }
            val atBlank = blankScanPos >= localLimit ||
                localRawData[blankScanPos] == C.NEWLINE_BYTE || localRawData[blankScanPos] == C.CR_BYTE
            if (atBlank) {
                blankLines++
                position = blankScanPos
                if (position >= localLimit) break
                continue
            }

            // No indentation threshold to check here (see the KDoc above) — just skip this
            // line's leading whitespace and look at what actually comes next.
            position = blankScanPos
            val lineStart = position
            val leadByte = localRawData[position]
            val leadIsColonSeparator = leadByte == C.COLON_BYTE && run {
                val afterColon = position + 1
                afterColon >= localLimit || localRawData[afterColon] == C.SPACE_BYTE ||
                    localRawData[afterColon] == C.NEWLINE_BYTE || localRawData[afterColon] == C.CR_BYTE ||
                    localRawData[afterColon] == C.TAB_BYTE
            }
            if (leadByte == C.COMMA_BYTE || leadByte == C.RIGHT_BRACE_BYTE || leadByte == C.RIGHT_BRACKET_BYTE ||
                leadByte == C.HASH_BYTE || leadIsColonSeparator || isDocumentMarker() || isDocumentEndMarker()
            ) {
                // This line is nothing but the scalar's own terminator (or a comment) — not a
                // continuation. A comment can't appear inside a still-open plain scalar's line-
                // folding, so a comment-only line ends the scalar right here too, regardless of
                // what follows it — same as it would outside any fold (see CML9: the line after
                // a comment that interrupts a plain scalar needs its own comma, not a fold).
                // Same rewind rule as the block version: undo blank lines tentatively scanned
                // past, since the caller needs to see them fresh either way.
                position = if (folded != null) beforeNewline else scalarEndPosition
                break
            }

            // Scan this continuation line's own content with the same stop rules a single-line
            // flow scalar already uses (mirrors the scan at the top of readPlainScalarOrMapping).
            var scanPos = lineStart
            while (scanPos < localLimit) {
                val currentByte = localRawData[scanPos]
                when {
                    currentByte == C.COLON_BYTE -> {
                        val afterColon = scanPos + 1
                        if (afterColon >= localLimit ||
                            localRawData[afterColon] == C.SPACE_BYTE ||
                            localRawData[afterColon] == C.NEWLINE_BYTE ||
                            localRawData[afterColon] == C.CR_BYTE ||
                            localRawData[afterColon] == C.TAB_BYTE
                        ) break
                        scanPos++
                    }

                    currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE -> break
                    currentByte == C.HASH_BYTE -> {
                        if (scanPos > lineStart &&
                            (localRawData[scanPos - 1] == C.SPACE_BYTE || localRawData[scanPos - 1] == C.TAB_BYTE)
                        ) break
                        scanPos++
                    }

                    currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE -> break
                    else -> scanPos++
                }
            }

            val lineEnd = trimTrailingSpaces(lineStart, scanPos)
            val lineText = localRawData.decodeToString(lineStart, lineEnd)

            if (folded == null) folded = StringBuilder(firstLine)
            if (blankLines > 0) repeat(blankLines) { folded.append('\n') } else folded.append(' ')
            folded.append(lineText)
            blankLines = 0

            position = scanPos
            if (position >= localLimit) break
            // This line's own scan may have stopped at a mid-line terminator (not a newline) —
            // if so the scalar is done, don't loop around expecting another continuation line.
            if (localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) break
        }

        return folded?.toString()
    }

    private fun readPlainScalar(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE,
        allowMappingRedirect: Boolean = true
    ): Any? =
        readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect)

    // ── Key reading ────────────────────────────────────────────────────────────

    /**
     * Reads a mapping key. Keys are plain scalars ending at ':'.
     * Quoted keys are supported.
     */
    internal fun readKey(inFlow: Boolean): String? {
        skipInlineWhitespace()
        val localLimit = limit
        val localRawData = rawData
        if (position >= localLimit) return null

        // Alias as key: "*name" resolves immediately to the aliased node's own value, stringified
        // the same way an explicit-key node would be (e.g. "*b : *a" below "&a a: &b b" — the
        // key is whatever "&b" was bound to). Only recognized with no anchor/tag prefix before
        // it; combining both isn't a case any test exercises and has no clear meaning anyway.
        if (localRawData[position] == C.ASTERISK_BYTE) {
            return stringifyExplicitMappingKey(readAlias())
        }

        // An anchor and/or tag may prefix a key (e.g. "&a5 !!str key5:", "!!str &a10 key10:") —
        // a tag has no JSON representation on a key, so it's dropped the same way an ordinary
        // tagged value's tag is; an anchor, though, still needs to end up bound to the key's own
        // text once known, so later aliases can resolve it (e.g. "&a a: ..." then "*a" elsewhere).
        var anchorName: String? = null
        val positionBeforePrefixes = position
        while (position < localLimit &&
            (localRawData[position] == C.AMPERSAND_BYTE || localRawData[position] == C.EXCLAMATION_BYTE)
        ) {
            val isAnchor = localRawData[position] == C.AMPERSAND_BYTE
            if (isAnchor) position++ // consume '&'
            val prefixStart = position
            while (position < localLimit) {
                val prefixByte = localRawData[position]
                if (prefixByte == C.SPACE_BYTE || prefixByte == C.TAB_BYTE ||
                    prefixByte == C.NEWLINE_BYTE || prefixByte == C.CR_BYTE ||
                    prefixByte == C.COMMA_BYTE || prefixByte == C.RIGHT_BRACE_BYTE || prefixByte == C.RIGHT_BRACKET_BYTE
                ) break
                position++
            }
            if (isAnchor) anchorName = localRawData.decodeToString(prefixStart, position)
            skipInlineWhitespace()
        }
        // Having consumed an anchor/tag prefix commits us to there being a real key afterward —
        // unlike the "nothing here at all" case below, silently returning null having already
        // eaten those bytes would let a genuinely invalid "dangling" anchor/tag (nothing valid
        // following it) masquerade as "no more keys in this mapping" instead of erroring.
        val hadPrefixes = position != positionBeforePrefixes
        if (position >= localLimit) {
            if (hadPrefixes) yamlError(C.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
            return null
        }
        // An anchor can't wrap an alias reference here either — same rule readAnchoredValue
        // already enforces for values (an alias points at an existing node, it isn't itself a
        // node that a new anchor can attach to).
        if (anchorName != null && localRawData[position] == C.ASTERISK_BYTE) {
            yamlError("${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX}$anchorName${C.ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX}")
        }
        val key = when (localRawData[position]) {
            C.DOUBLE_QUOTE_BYTE -> readQuotedKeyRejectingMultiLine(inFlow) { readDoubleQuotedString() }
            C.SINGLE_QUOTE_BYTE -> readQuotedKeyRejectingMultiLine(inFlow) { readSingleQuotedString() }
            else -> {
                val startPosition = position
                while (position < localLimit) {
                    val currentByte = localRawData[position]
                    if (currentByte == C.COLON_BYTE) {
                        val nextPosition = position + 1
                        if (nextPosition >= localLimit ||
                            localRawData[nextPosition] == C.SPACE_BYTE ||
                            localRawData[nextPosition] == C.NEWLINE_BYTE ||
                            localRawData[nextPosition] == C.CR_BYTE ||
                            localRawData[nextPosition] == C.TAB_BYTE
                        ) break
                    }
                    if (currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE) break
                    // An inline comment ends the key the same way it ends a plain scalar value
                    // (see readPlainScalarOrMapping) — only when preceded by whitespace, so
                    // "a#b" stays one key while "a #b" ends the key at "a".
                    if (currentByte == C.HASH_BYTE && position > startPosition &&
                        (localRawData[position - 1] == C.SPACE_BYTE || localRawData[position - 1] == C.TAB_BYTE)
                    ) break
                    if (inFlow && (currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE)) break
                    position++
                }
                val endPosition = trimTrailingSpaces(startPosition, position)
                if (endPosition == startPosition) {
                    // A bare ':' with nothing before it is a valid empty-string key (e.g.
                    // ": value", or repeated ": a" / ": b" pairs) — the loop above breaks on
                    // the very first byte in that case, without advancing. A tag can be the
                    // entire key by itself the same way (e.g. "!!str : bar" is an empty-string
                    // key) — a prefix followed directly by ':' isn't "dangling", it's this same
                    // empty-key shape with the tag already consumed. Anything else here (a
                    // newline, EOF) really is "no more mapping to read" — or, if a prefix was
                    // consumed first, a genuinely dangling one.
                    if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
                        ""
                    } else if (hadPrefixes) {
                        yamlError(C.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
                    } else {
                        null
                    }
                } else {
                    val firstLine = localRawData.decodeToString(startPosition, endPosition)
                    // A flow mapping key can fold across lines the same way any other flow plain
                    // scalar does (e.g. "{ matches\n% : 20 }" — the key is "matches %") — block-
                    // context keys never reach here still sitting on a newline, since a colon
                    // must follow on the same line there.
                    if (inFlow && position < localLimit &&
                        (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)
                    ) {
                        foldFlowPlainScalarContinuation(firstLine) ?: firstLine
                    } else {
                        firstLine
                    }
                }
            }
        }
        if (key != null && anchorName != null) {
            anchorTable[anchorName] = key
        }
        return key
    }

    /**
     * Reads a quoted key via [readQuoted], then — in block context only — rejects it if it
     * spanned more than one line. An implicit block-mapping key (no `?` indicator) must fit on a
     * single line, unlike a quoted scalar used as an ordinary value (which folds across lines
     * fine); a flow-mapping key has no such restriction, since the surrounding brackets already
     * give the parser an unambiguous boundary (confirmed by 9BXH/9SA2 expecting a folded multi-
     * line flow key to succeed, vs. JKF3 expecting the equivalent block key to fail).
     */
    private inline fun readQuotedKeyRejectingMultiLine(inFlow: Boolean, readQuoted: () -> String): String {
        val startPosition = position
        val text = readQuoted()
        if (inFlow) return text
        var scanPosition = startPosition
        while (scanPosition < position) {
            if (rawData[scanPosition] == C.NEWLINE_BYTE || rawData[scanPosition] == C.CR_BYTE) {
                yamlError(C.ERR_IMPLICIT_KEY_MULTILINE)
            }
            scanPosition++
        }
        return text
    }

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

    /** Bitwise digit check — no `.toChar()`, no range object allocation. */
    internal fun isDigit(currentByte: Byte): Boolean =
        (currentByte - C.DIGIT_LOWER_BOUND).toUByte() <= (C.DIGIT_UPPER_BOUND - C.DIGIT_LOWER_BOUND).toUByte()

    // ── Error handling ────────────────────────────────────────────────────────

    internal fun yamlError(message: String): Nothing {
        throw GhostYamlException("$message (position=$position)")
    }

    /** Thrown by both [readAllDocuments] overloads when a document consumed no input. */
    private fun noProgressError(): Nothing {
        yamlError("${C.ERR_PARSER_NO_PROGRESS_PREFIX}$position${C.ERR_PARSER_NO_PROGRESS_SUFFIX}")
    }

    /**
     * Called by both [readAllDocuments] overloads right after a document's value is read.
     * What may legally follow a document's value is: end of input, a `---` document-start
     * marker (the next document), or a `%` directive (the next document's directives) — anything
     * else is leftover content that the value's own reader stopped in front of without
     * understanding, e.g. a stray closing bracket or a bare word after a flow collection closed.
     */
    private fun rejectTrailingGarbageAfterDocument() {
        skipWhitespaceAndComments()
        if (position >= limit) return
        val currentByte = rawData[position]
        if (currentByte != C.DASH_BYTE && currentByte != C.PERCENT_BYTE) {
            yamlError(C.ERR_UNEXPECTED_AFTER_DOCUMENT_VALUE)
        }
    }

    /**
     * Called by both [readAllDocuments] overloads right before deciding whether the next thing
     * is a directive. Directives only apply to a document that hasn't started yet — legal at the
     * very start of the stream, or right after an explicit `...` — not merely because the
     * previous document's content happened to end (e.g. right after an implicit `---`-to-`---`
     * transition with no `...` in between).
     */
    private fun requireExplicitEndBeforeDirectives(previousDocumentExplicitlyEnded: Boolean) {
        if (previousDocumentExplicitlyEnded) return
        if (position < limit && rawData[position] == C.PERCENT_BYTE) {
            yamlError(C.ERR_DIRECTIVES_NEED_DOC_END)
        }
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

    var strictMode: Boolean = false
    var coerceStringsToNumbers: Boolean = false
    var coerceBooleans: Boolean = false
    var maxDepth: Int = C.MAX_DEPTH
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize

    @PublishedApi
    internal val tokenEndObject = -1

    @PublishedApi
    internal val tokenUnknownName = -2

    // Every method below is a thin delegate to an identically-named `xxxImpl` extension function
    // in GhostYamlCursorTraversal.kt — see that file's header comment for why these stay real
    // members here (public API consumed by KSP-generated code in other Gradle modules) instead of
    // becoming extension functions the way the other subsystems below do.

    fun beginObject() = beginObjectImpl()
    fun endObject() = endObjectImpl()
    fun selectNameAndConsume(options: JsonReaderOptions): Int = selectNameAndConsumeImpl(options)
    fun selectString(options: JsonReaderOptions): Int = selectStringImpl(options)
    fun skipValue() = skipValueImpl()
    fun isNextNullValue(): Boolean = isNextNullValueImpl()
    fun consumeNull() = consumeNullImpl()

    /** Reads a YAML string, or `null` when the next value is YAML null. */
    fun nextStringOrNull(): String? = nextStringOrNullImpl()

    /** Reads a YAML int, or `null` when the next value is YAML null. */
    fun nextIntOrNull(): Int? = nextIntOrNullImpl()

    /** Reads a YAML long, or `null` when the next value is YAML null. */
    open fun nextLongOrNull(): Long? = nextLongOrNullImpl()

    /** Reads a YAML boolean, or `null` when the next value is YAML null. */
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
    fun throwError(message: String): Nothing = throwErrorImpl(message)
    fun peekStringField(name: String): String? = peekStringFieldImpl(name)

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
}
