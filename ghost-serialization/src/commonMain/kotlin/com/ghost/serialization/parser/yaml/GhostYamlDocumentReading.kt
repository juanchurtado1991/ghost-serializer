package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/** Resets the reader's state to process a new byte payload. */
@OptIn(InternalGhostApi::class)
fun GhostYamlFlatReader.reset(newData: ByteArray) {
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
    maxDepth = SC.MAX_DEPTH
    maxCollectionSize = GhostHeuristics.maxCollectionSize
}

/** Reads a single YAML document; returns a Map, List, String, Long, Double, Boolean, or null. */
fun GhostYamlFlatReader.readDocument(): Any? {
    anchorTable.clear()
    tagDirectives.clear()
    skipDirectivesAndDocumentStart()
    skipWhitespaceAndComments()
    if (position >= limit) return emptyMap<String, Any?>()
    return readValue(indent = SC.INDENT_UNSET, inFlow = false)
}

/** Reads all YAML documents in the source (separated by `---`). */
fun GhostYamlFlatReader.readAllDocuments(): List<Any?> =
    readAllDocumentsImpl { readValue(indent = SC.INDENT_UNSET, inFlow = false) }

/**
 * Reads every YAML document and deserializes each with [deserializeDocument].
 * Resets traversal state between documents so generated `GhostYamlSerializer` paths work.
 */
fun <T> GhostYamlFlatReader.readAllDocuments(deserializeDocument: (GhostYamlFlatReader) -> T): List<T> =
    readAllDocumentsImpl {
        prepareRootForCurrentDocument()
        val result = deserializeDocument(this)
        clearAfterDocument()
        result
    }

/**
 * Shared document-boundary loop for both [readAllDocuments] overloads: skips directives, `---`,
 * and `...` markers, then hands each document's value off to [readOneDocument] — the only part
 * that differs between reading a raw value and deserializing straight into a typed [T].
 */
private inline fun <T> GhostYamlFlatReader.readAllDocumentsImpl(readOneDocument: () -> T): List<T> {
    val results = mutableListOf<T>()
    val localLimit = limit
    var previousDocumentExplicitlyEnded = true
    while (position < localLimit) {
        anchorTable.clear()
        tagDirectives.clear()
        skipWhitespaceAndComments()
        if (position >= localLimit) break
        requireExplicitEndBeforeDirectives(previousDocumentExplicitlyEnded = previousDocumentExplicitlyEnded)
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
        results.add(readOneDocument())
        val sawExplicitEnd = skipDocumentEnd()
        if (!sawExplicitEnd) rejectTrailingGarbageAfterDocument()
        previousDocumentExplicitlyEnded = sawExplicitEnd
        if (position == iterationStart) noProgressError()
    }
    return results
}

/** Thrown by both [readAllDocuments] overloads when a document consumed no input. */
private fun GhostYamlFlatReader.noProgressError(): Nothing {
    yamlError(message = "${EM.ERR_PARSER_NO_PROGRESS_PREFIX}$position${EM.ERR_PARSER_NO_PROGRESS_SUFFIX}")
}

/**
 * Called by both [readAllDocuments] overloads after a document's value is read. What may
 * legally follow is: EOF, a `---` document-start marker, or a `%` directive — anything else
 * is leftover content the value's reader stopped in front of without understanding.
 */
private fun GhostYamlFlatReader.rejectTrailingGarbageAfterDocument() {
    skipWhitespaceAndComments()
    if (position >= limit) return
    val currentByte = rawData[position]
    if (currentByte != TOK.DASH_BYTE && currentByte != TOK.PERCENT_BYTE) {
        yamlError(message = EM.ERR_UNEXPECTED_AFTER_DOCUMENT_VALUE)
    }
}

/**
 * Called by both [readAllDocuments] overloads before deciding whether the next thing is a
 * directive. Directives only apply to a document that hasn't started yet — legal at stream
 * start or right after an explicit `...`, not merely because the previous document ended
 * (e.g. an implicit `---`-to-`---` transition with no `...` between).
 */
private fun GhostYamlFlatReader.requireExplicitEndBeforeDirectives(previousDocumentExplicitlyEnded: Boolean) {
    if (previousDocumentExplicitlyEnded) return
    if (position < limit && rawData[position] == TOK.PERCENT_BYTE) {
        yamlError(message = EM.ERR_DIRECTIVES_NEED_DOC_END)
    }
}
