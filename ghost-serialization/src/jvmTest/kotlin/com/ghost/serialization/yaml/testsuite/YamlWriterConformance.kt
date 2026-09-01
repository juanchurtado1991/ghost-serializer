package com.ghost.serialization.yaml.testsuite

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader

/**
 * Decodes [case]'s `in.yaml` with the reader. Null if the reader can't decode it — not a
 * writer-harness concern, and self-adjusting: a case that starts throwing once a reader bug is
 * fixed elsewhere just drops out of the writer conformance suite with no coordination needed.
 */
internal fun decodeOriginal(case: YamlTestSuiteCase): List<Any?>? =
    try {
        GhostYamlFlatReader(case.inYamlBytes).readAllDocuments()
    } catch (e: Exception) {
        null
    }

/** Re-encodes [original] with [GhostYamlTreeWriter] and re-decodes the result with the reader. */
internal fun reEncodeAndReDecode(original: List<Any?>): Pair<String, List<Any?>>? {
    val text = GhostYamlTreeWriter.encodeAll(original)
    return try {
        text to GhostYamlFlatReader(text.encodeToByteArray()).readAllDocuments()
    } catch (e: Exception) {
        null
    }
}

/** True if decode(in.yaml) -> encode -> decode round-trips to a tree identical to the original. */
internal fun writerRoundTripMatches(case: YamlTestSuiteCase): Boolean {
    val original = decodeOriginal(case) ?: return false
    val (_, reDecoded) = reEncodeAndReDecode(original) ?: return false
    return original.size == reDecoded.size &&
        original.indices.all { i -> ghostValuesEqual(original[i], reDecoded[i]) }
}

/**
 * Symmetric deep-equality between two Ghost-native decoded values (both from
 * `GhostYamlFlatReader`). Deliberately separate from [deepEquals] in
 * `YamlTestSuiteJsonComparison.kt`, which excludes `STR_TAG_KEY` from only one side since a JSON
 * fixture never has it — reusing that here would wrongly flag a correctly round-tripped `_tag`
 * entry as a mismatch.
 */
private fun ghostValuesEqual(original: Any?, reDecoded: Any?): Boolean = when {
    original is Map<*, *> && reDecoded is Map<*, *> ->
        original.keys == reDecoded.keys &&
            original.keys.all { key -> ghostValuesEqual(original[key], reDecoded[key]) }

    original is List<*> && reDecoded is List<*> ->
        original.size == reDecoded.size &&
            original.indices.all { i -> ghostValuesEqual(original[i], reDecoded[i]) }

    original is Number && reDecoded is Number -> original.toDouble() == reDecoded.toDouble()

    else -> original == reDecoded
}

/**
 * True if a second, independent parser (kaml) accepts Ghost's re-encoded output — not a
 * byte-match target, just "does an independent implementation consider this valid YAML."
 */
internal fun writerOutputIsKamlAcceptable(case: YamlTestSuiteCase): Boolean {
    val original = decodeOriginal(case) ?: return true
    if (original.size != 1) return true // kaml's Yaml.default targets one document
    val text = GhostYamlTreeWriter.encode(original[0])
    return try {
        Yaml.default.parseToYamlNode(text)
        true
    } catch (e: YamlException) {
        false
    }
}
