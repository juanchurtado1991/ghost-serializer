package com.ghost.serialization.yaml.testsuite

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader

/**
 * Decodes [case]'s `in.yaml` with the reader. Null if the reader itself can't decode it — not a
 * writer-harness concern either way, and self-adjusting: a case that starts throwing once a
 * reader bug is fixed elsewhere silently drops out of the writer conformance suite with no
 * coordination needed between the two harnesses.
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
 * Symmetric deep-equality between two Ghost-native decoded values (both sides from
 * `GhostYamlFlatReader`, not a JSON fixture) — deliberately
 * separate from [deepEquals] in `YamlTestSuiteJsonComparison.kt`, which asymmetrically excludes
 * `STR_TAG_KEY` from only the Ghost-decoded side
 * because a JSON fixture never has that synthetic key at all. Reusing that function here (both
 * sides Ghost-native) would wrongly treat a `_tag` entry that round-tripped correctly as a
 * mismatch, since it'd be filtered from [original] but not [reDecoded].
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
 * True if a second, independent parser (kaml) accepts Ghost's own re-encoded output — not a
 * byte-match target (Ghost's flow style differs from every reference emitter in the vendored
 * snapshot), just "does an independent implementation consider this valid YAML."
 */
internal fun writerOutputIsKamlAcceptable(case: YamlTestSuiteCase): Boolean {
    val original = decodeOriginal(case) ?: return true
    if (original.size != 1) return true // kaml's Yaml.default targets one document; multi-doc is out of scope here.
    val text = GhostYamlTreeWriter.encode(original[0])
    return try {
        Yaml.default.parseToYamlNode(text)
        true
    } catch (e: YamlException) {
        false
    }
}
