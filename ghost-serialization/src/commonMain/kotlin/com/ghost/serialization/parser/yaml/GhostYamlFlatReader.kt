package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostJsonPathTracker
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.GhostJsonConstants as JC
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.exception.hintForYamlError
import com.ghost.serialization.yaml.GhostYamlConstants as C


/**
 * High-performance YAML reader operating on a [ByteArray] with minimal intermediate allocations.
 *
 * Compares bytes directly (avoids `.toChar()` on hot paths); control bytes live in
 * `GhostYamlConstants`; digit/whitespace/alpha checks use bitwise ops; field matching operates
 * on raw bytes with string decoding deferred until final value extraction.
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
     * Whether a tab appears in the current line's leading whitespace between the counted
     * [currentIndent] spaces and the first non-whitespace byte. Tabs have no fixed column width,
     * so YAML forbids them in indentation that opens/extends a block mapping/sequence, but
     * they're harmless once a scalar's content has started.
     */
    internal var indentHasTab: Boolean = false

    /** Depth counter — guards against stack overflow on extreme nesting. */
    internal var depth: Int = 0

    /** Table of defined anchors for the current document. */
    internal val anchorTable = HashMap<String, Any?>()

    /** Table of defined tag directives for the current document. */
    internal val tagDirectives = HashMap<String, String>()

    /** Resets the reader's state to process a new byte payload. */
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
        pathTracker.reset()
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
            // A bare `...` with no preceding `---` isn't an empty document — it's the end marker
            // for a document that never started. Consume it and loop instead of reading it as
            // a null-valued document.
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
        // Distinct from `indent`: if this value is a plain scalar, later lines must indent more
        // than *this* to keep folding into it. Normally equals `indent`, but diverges for a
        // mapping value starting on its own line: `indent` there is this value's auto-detected
        // column (needed if it turns out to be a nested block collection), while a plain
        // scalar's fold boundary per spec is the *enclosing mapping's* indent, not the value's.
        foldIndent: Int = indent
    ): Any? {
        skipInlineWhitespace()
        val localLimit = limit
        if (position >= localLimit) {
            // A bare "!!str" with nothing after it, even at EOF, still resolves to an empty
            // string, not "no value at all" — same as trailing whitespace/newline instead of EOF.
            return if (expectedTag == GhostYamlTags.TAG_STR) "" else null
        }

        val currentByte = rawData[position]
        return when (currentByte) {
            C.PIPE_BYTE, C.GT_BYTE -> readBlockScalar(currentByte, indent)
            C.LEFT_BRACE_BYTE -> readFlowCollectionOrMappingKey(indent, inFlow) { readFlowMapping() }
            C.LEFT_BRACKET_BYTE -> readFlowCollectionOrMappingKey(indent, inFlow) { readFlowSequence() }
            C.EXCLAMATION_BYTE -> readTaggedValue(indent, inFlow)
            C.AMPERSAND_BYTE -> readAnchoredValueOrMappingKey(indent, inFlow, strictDedent)
            C.ASTERISK_BYTE -> readAliasOrMappingKey(indent, inFlow)
            C.DOUBLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent, inFlow) { readDoubleQuotedString() }
            C.SINGLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent, inFlow) { readSingleQuotedString() }
            C.DOT_BYTE -> if (isDocumentEndMarker()) null else readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
            // '%' is reserved for directives, never a plain scalar start (no "followed by a safe
            // character" exception like '-'/'?'/':' get) — a directive-shaped line where a value
            // is expected is invalid, not a scalar starting with '%'.
            C.PERCENT_BYTE -> yamlError(C.ERR_PLAIN_SCALAR_PERCENT)
            C.QUESTION_BYTE ->
                if (!inFlow && isExplicitKeyIndicator()) readBlockMapping(indent.coerceAtLeast(0))
                else readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
            C.DASH_BYTE -> {
                // Negative number "-42", block sequence "- item", or doc separator "---".
                val nextByte = if (position + 1 < localLimit) rawData[position + 1] else 0
                when {
                    expectedTag != GhostYamlTags.TAG_STR && isDigit(nextByte) -> readNumber()
                    nextByte == C.SPACE_BYTE || nextByte == C.NEWLINE_BYTE || nextByte == C.CR_BYTE ||
                        nextByte == C.TAB_BYTE || position + 1 >= localLimit ->
                        readBlockSequence(indent)

                    isDocumentMarker() -> null
                    else -> readPlainScalar(indent, inFlow, expectedTag, allowMappingRedirect)
                }
            }

            else -> readPlainScalarOrMapping(indent, inFlow, expectedTag, allowMappingRedirect, foldIndent)
        }
    }

    /**
     * Reads a quoted scalar via [readQuoted], then checks whether a `:` follows — a quoted
     * string can be a mapping key, not just a value (e.g. `"400":`). [readPlainScalarOrMapping]
     * already does this colon-scan for *bare* keys, but readValue's dispatch never reaches it
     * for quoted content. Without this check a quoted key's value would be misread as the
     * quoted string itself, dropping everything nested under it.
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
        // where it is and let the caller (a flow sequence entry may be an implicit single-pair
        // mapping) decide.
        if (inFlow || !isMappingKey) return text
        position = startPosition
        return readBlockMapping(indent.coerceAtLeast(0))
    }

    /**
     * Reads a flow collection ([readCollection]), then — mirroring
     * [readQuotedScalarOrMappingKey] — checks whether a `:` follows: a flow collection can
     * itself be a block-mapping key (e.g. `[flow]: block`), not just a value. Without this,
     * `[flow]: block` would read `[flow]` as a complete document value and choke on `: block`.
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
                                // This colon is an *inline* value/key-node on the same line as an
                                // enclosing key's own ':' (e.g. "b: c" in "a: b: c") — no fresh,
                                // more-indented line for a nested mapping, so it's ambiguous, not
                                // a legal redirect. yaml-test-suite case ZCZ6.
                                yamlError(C.ERR_UNEXPECTED_INLINE_NESTED_MAPPING_COLON)
                            }
                            // Rewind and parse as block mapping
                            position = startPosition
                            return readBlockMapping(indent.coerceAtLeast(0))
                        }
                        // No block mapping to redirect into inside a flow collection — stop the
                        // scalar here and let the caller decide what the colon means.
                        break
                    }
                    scanPosition++
                }

                currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE -> break
                currentByte == C.HASH_BYTE -> {
                    // Inline comment ends the scalar before '#', only if preceded by whitespace
                    // ("d#X" isn't a comment, "d #X" is).
                    if (scanPosition > startPosition &&
                        (localRawData[scanPosition - 1] == C.SPACE_BYTE || localRawData[scanPosition - 1] == C.TAB_BYTE)
                    ) break
                    scanPosition++
                }

                inFlow && (currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE) -> break
                else -> scanPosition++
            }
        }

        val endPosition = trimTrailingSpaces(startPosition, scanPosition)
        position = scanPosition

        // A bare "-" is a valid plain-scalar start only when followed by a "safe" character
        // (ns-plain-first's rule for '-'/'?'/':') — in flow context that excludes flow
        // indicators (",[]{}"), so a lone "-" touching one (e.g. "[-, -]") is neither a block
        // sequence indicator (flow has none) nor a valid plain scalar.
        if (inFlow && endPosition - startPosition == 1 && localRawData[startPosition] == C.DASH_BYTE) {
            yamlError(C.ERR_LONE_DASH_IN_FLOW)
        }

        // Plain scalars continue onto following lines: more-indented lines in block context
        // ([foldPlainScalarContinuation]), any non-terminator line in flow context
        // ([foldFlowPlainScalarContinuation]). Folding rule either way: a single newline becomes
        // a space; N blank lines become N newlines (same as readBlockScalarContent's ">" style).
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
     * Consumes following lines that continue a plain scalar (each more indented than [indent],
     * the enclosing block's indentation), folding them onto [firstLine] per YAML's line-folding
     * rule. Returns `null` (without moving [position]) if the next line isn't a continuation, so
     * the caller falls back to its single-line result.
     */
    private fun foldPlainScalarContinuation(indent: Int, firstLine: String): String? {
        val localRawData = rawData
        val localLimit = limit
        val scalarEndPosition = position
        var folded: StringBuilder? = null
        var blankLines = 0

        while (true) {
            val beforeNewline = position
            // Blank-ness is judged after skipping *all* leading whitespace (spaces and tabs) — a
            // line that's purely whitespace is blank even if it includes a tab.
            val blankScan = scanFoldBlankLine(localRawData, position, localLimit)
            if (blankScan.isBlank) {
                blankLines++
                position = blankScan.contentStart
                if (position >= localLimit) break
                continue
            }
            position = blankScan.afterNewline

            // How "indented" this line is (continuation-vs-dedent) is judged by real spaces only
            // — a tab can't establish block-structure indentation (see recomputeCurrentIndent/
            // indentHasTab), else a tab at a sibling key's indentation would falsely read as
            // "still a continuation" instead of ambiguous/invalid. Once continuation is
            // confirmed, tabs after those spaces are ordinary leading whitespace like the spaces.
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
                // Not a continuation (dedented, sibling-level, or a new document). If we already
                // folded a real continuation line, rewind just past it (trailing blank lines
                // aren't part of the value); otherwise rewind to right after the first line,
                // undoing any tentatively-scanned blank lines so the caller sees them again.
                position = if (folded != null) beforeNewline else scalarEndPosition
                break
            }

            val lineStart = position
            var sawComment = false
            while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
                val currentByte = localRawData[position]
                // A comment ends this line the same way it ends a single-line plain scalar (only
                // when preceded by whitespace) — and since a comment can't appear inside a
                // still-open fold, it ends the whole scalar here, not just this line (case BF9H).
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
            // scalar's fold can't contain a nested key/value pair (case 2CMS).
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
            appendFoldedLine(folded, blankLines, lineText)
            blankLines = 0

            // Leave position at the '#' — the caller's skipWhitespaceAndComments handles it.
            if (sawComment) break
            if (position >= localLimit) break
        }

        return folded?.toString()
    }

    /**
     * Flow-context counterpart to [foldPlainScalarContinuation]. Unlike block context, a flow
     * collection isn't indentation-bounded once opened (delimited by its closing bracket/brace
     * instead), so there's no indentation threshold here — continuation is decided purely by
     * what the next line starts with, using the same terminators (`,`, `]`, `}`, a real `:` key
     * separator, or a whitespace-preceded `#` comment) that end a single-line flow scalar.
     */
    private fun foldFlowPlainScalarContinuation(firstLine: String): String? {
        val localRawData = rawData
        val localLimit = limit
        val scalarEndPosition = position
        var folded: StringBuilder? = null
        var blankLines = 0

        while (true) {
            val beforeNewline = position
            val blankScan = scanFoldBlankLine(localRawData, position, localLimit)
            if (blankScan.isBlank) {
                blankLines++
                position = blankScan.contentStart
                if (position >= localLimit) break
                continue
            }

            // No indentation threshold here (see KDoc above) — skip this line's leading
            // whitespace and look at what comes next.
            position = blankScan.contentStart
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
                // continuation. A comment can't appear inside a still-open fold, so a
                // comment-only line ends the scalar here too regardless of what follows
                // (case CML9). Same rewind rule as the block version.
                position = if (folded != null) beforeNewline else scalarEndPosition
                break
            }

            // Scan this line's content with the same stop rules a single-line flow scalar uses
            // (mirrors the scan at the top of readPlainScalarOrMapping).
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
            appendFoldedLine(folded, blankLines, lineText)
            blankLines = 0

            position = scanPos
            if (position >= localLimit) break
            // If the scan stopped at a mid-line terminator (not a newline), the scalar is done —
            // don't loop expecting another continuation line.
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

        // Alias as key: "*name" resolves immediately to the aliased node's value, stringified the
        // same way an explicit-key node would be. Only recognized with no anchor/tag prefix
        // before it; combining both has no clear meaning and isn't exercised by any test.
        if (localRawData[position] == C.ASTERISK_BYTE) {
            return stringifyExplicitMappingKey(readAlias())
        }

        // An anchor and/or tag may prefix a key (e.g. "&a5 !!str key5:"). A tag has no JSON
        // representation on a key, so it's dropped like an ordinary tagged value's tag; an
        // anchor still needs binding to the key's text so later aliases can resolve it.
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
        // Having consumed an anchor/tag prefix commits us to a real key afterward — silently
        // returning null after eating those bytes would let a dangling anchor/tag masquerade as
        // "no more keys" instead of erroring.
        val hadPrefixes = position != positionBeforePrefixes
        if (position >= localLimit) {
            if (hadPrefixes) yamlError(C.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
            return null
        }
        // An anchor can't wrap an alias reference here either — same rule readAnchoredValue
        // enforces for values (an alias points at an existing node, not one a new anchor can attach to).
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
                    // An inline comment ends the key like it ends a plain scalar value, only
                    // when preceded by whitespace: "a#b" stays one key, "a #b" ends at "a".
                    if (currentByte == C.HASH_BYTE && position > startPosition &&
                        (localRawData[position - 1] == C.SPACE_BYTE || localRawData[position - 1] == C.TAB_BYTE)
                    ) break
                    if (inFlow && (currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE)) break
                    position++
                }
                val endPosition = trimTrailingSpaces(startPosition, position)
                if (endPosition == startPosition) {
                    // A bare ':' with nothing before it is a valid empty-string key (e.g.
                    // ": value") — the loop above breaks on the first byte without advancing.
                    // A prefix followed directly by ':' is this same empty-key shape, not
                    // dangling. Anything else (newline, EOF) is "no more mapping to read", or a
                    // genuinely dangling prefix if one was consumed.
                    if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
                        ""
                    } else if (hadPrefixes) {
                        yamlError(C.ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY)
                    } else {
                        null
                    }
                } else {
                    val firstLine = localRawData.decodeToString(startPosition, endPosition)
                    // A flow mapping key can fold across lines like any other flow plain scalar
                    // — block-context keys never reach here sitting on a newline, since a colon
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
     * single line, unlike an ordinary quoted-scalar value (which folds fine); a flow-mapping key
     * has no such restriction since its brackets give an unambiguous boundary (cases 9BXH/9SA2
     * expect a folded multi-line flow key to succeed, JKF3 expects the block equivalent to fail).
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
        // Parse phase: no AST cursor yet — keep path at root so we never invent a fake location.
        throw GhostYamlException(
            baseMessage = "$message${C.ERR_AT_POSITION_PAREN_PREFIX}$position${C.ERR_AT_POSITION_PAREN_SUFFIX}",
            path = "$",
            hint = hintForYamlError(message),
        )
    }

    /** Thrown by both [readAllDocuments] overloads when a document consumed no input. */
    private fun noProgressError(): Nothing {
        yamlError("${C.ERR_PARSER_NO_PROGRESS_PREFIX}$position${C.ERR_PARSER_NO_PROGRESS_SUFFIX}")
    }

    /**
     * Called by both [readAllDocuments] overloads after a document's value is read. What may
     * legally follow is: EOF, a `---` document-start marker, or a `%` directive — anything else
     * is leftover content the value's reader stopped in front of without understanding.
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
     * Called by both [readAllDocuments] overloads before deciding whether the next thing is a
     * directive. Directives only apply to a document that hasn't started yet — legal at stream
     * start or right after an explicit `...`, not merely because the previous document ended
     * (e.g. an implicit `---`-to-`---` transition with no `...` between).
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

    /**
     * Cursor-phase JSONPath breadcrumbs (same tracker as JSON). Format only on throw.
     * Parse-phase [yamlError] does not use this stack (path stays `"$"`).
     */
    internal val pathTracker: GhostJsonPathTracker = GhostJsonPathTracker()

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

    /**
     * Throws for a missing required field, pushing [jsonName] onto the JSONPath so the
     * exception points at `$.….<jsonName>` (validation runs before [endObject]).
     */
    fun throwMissingRequiredField(jsonName: String): Nothing {
        pathTracker.pushKey(jsonName)
        throwError("${JC.ERR_REQUIRED_FIELD_PREFIX}$jsonName${JC.ERR_REQUIRED_FIELD_SUFFIX}")
    }

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
