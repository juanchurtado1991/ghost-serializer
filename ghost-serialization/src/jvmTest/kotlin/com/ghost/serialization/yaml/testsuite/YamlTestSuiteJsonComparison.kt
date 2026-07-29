@file:OptIn(ExperimentalSerializationApi::class)

package com.ghost.serialization.yaml.testsuite

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses yaml-test-suite's `in.json` fixture text into a list of top-level JSON values, one per
 * YAML document — mirrors [com.ghost.serialization.parser.yaml.GhostYamlFlatReader]'s
 * `readAllDocuments()` one-value-per-document semantics.
 *
 * Must use [DecodeSequenceMode.WHITESPACE_SEPARATED], never the default `AUTO_DETECT`: a
 * multi-document `in.json` (e.g. case `9KAX`) is several concatenated top-level JSON values, not
 * a JSON array — but `AUTO_DETECT` would misread a *single* document whose own top-level value
 * happens to be a JSON array (e.g. case `229Q`) as multiple documents.
 */
internal fun decodeJsonDocuments(text: String): List<JsonElement> {
    return Json.decodeToSequence<JsonElement>(
        text.byteInputStream(),
        DecodeSequenceMode.WHITESPACE_SEPARATED,
    ).toList()
}

/** Converts a [JsonElement] into the same generic shape Ghost decodes YAML into (see [deepEquals]). */
internal fun normalize(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.mapValues { (_, v) -> normalize(v) }
    is JsonArray -> element.map { normalize(it) }
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.booleanOrNull
        element.longOrNull != null -> element.longOrNull
        element.doubleOrNull != null -> element.doubleOrNull
        else -> element.content
    }
}

/**
 * Deep-equality between a value Ghost decoded (`Map<String,Any?>`/`List<Any?>`/`String`/`Long`/
 * `Double`/`Boolean`/`null`) and a [normalize]d JSON value, with lenient Long/Double leaf
 * comparison so representational noise (YAML's typed core schema vs. JSON's schema-less numbers)
 * doesn't manufacture false mismatches — real ones get triaged into [deviationsInValue] instead.
 */
internal fun deepEquals(ghostValue: Any?, jsonValue: Any?): Boolean {
    return when {
        ghostValue is Map<*, *> && jsonValue is Map<*, *> ->
            ghostValue.keys == jsonValue.keys &&
                ghostValue.keys.all { key -> deepEquals(ghostValue[key], jsonValue[key]) }

        ghostValue is List<*> && jsonValue is List<*> ->
            ghostValue.size == jsonValue.size &&
                ghostValue.indices.all { i -> deepEquals(ghostValue[i], jsonValue[i]) }

        ghostValue is Number && jsonValue is Number -> ghostValue.toDouble() == jsonValue.toDouble()

        else -> ghostValue == jsonValue
    }
}
