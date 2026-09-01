package com.ghost.serialization.yaml.testsuite

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.GhostYamlConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val OBJECT_START = '{'
private const val ARRAY_START = '['
private const val OBJECT_END = '}'
private const val ARRAY_END = ']'
private const val QUOTE = '"'
private const val ESCAPE = '\\'

/**
 * Parses yaml-test-suite's `in.json` fixture text into a list of top-level JSON values, one per
 * YAML document — mirrors `GhostYamlFlatReader.readAllDocuments()`'s semantics.
 *
 * Hand-rolled splitting instead of `Json.decodeToSequence(..., WHITESPACE_SEPARATED)`: that
 * experimental API misparses two back-to-back top-level JSON *arrays* (confirmed on case `JHB9`'s
 * `in.json`), throwing instead of starting a new value at the second `[`.
 * [splitTopLevelJsonValues] locates each value's boundary itself instead.
 */
internal fun decodeJsonDocuments(text: String): List<JsonElement> {
    return splitTopLevelJsonValues(text).map { Json.parseToJsonElement(it) }
}

/** Splits [text] into substrings, one per top-level JSON value (scalar, object, or array). */
internal fun splitTopLevelJsonValues(text: String): List<String> {
    val values = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && text[index].isWhitespace()) index++
        if (index >= text.length) break
        val start = index
        index = scanOneJsonValue(text, start)
        values.add(text.substring(start, index))
    }
    return values
}

/** Returns the exclusive end index of exactly one JSON value starting at [start] (past whitespace). */
private fun scanOneJsonValue(text: String, start: Int): Int {
    when (text[start]) {
        OBJECT_START, ARRAY_START -> {
            var depth = 0
            var inString = false
            var escaped = false
            var index = start
            while (index < text.length) {
                val c = text[index]
                index++
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == ESCAPE -> escaped = true
                        c == QUOTE -> inString = false
                    }
                } else {
                    when (c) {
                        QUOTE -> inString = true
                        OBJECT_START, ARRAY_START -> depth++
                        OBJECT_END, ARRAY_END -> {
                            depth--
                            if (depth == 0) return index
                        }
                    }
                }
            }
            return index
        }

        QUOTE -> {
            var index = start + 1
            var escaped = false
            while (index < text.length) {
                val c = text[index]
                index++
                if (escaped) escaped = false
                else if (c == ESCAPE) escaped = true
                else if (c == QUOTE) break
            }
            return index
        }

        else -> {
            // Bare scalar: ends at next whitespace or EOF.
            var index = start
            while (index < text.length && !text[index].isWhitespace()) index++
            return index
        }
    }
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
 * Deep-equality between a value Ghost decoded and a [normalize]d JSON value. Compares Long/Double
 * leaves leniently so YAML's typed schema vs. JSON's schema-less numbers doesn't manufacture false
 * mismatches — real ones go into [deviationsInValue] instead.
 *
 * Excludes [GhostYamlConstants.STR_TAG_KEY] from a Ghost-decoded map's keys: it's Ghost's own
 * synthetic field for preserving a custom tag, which JSON has no way to represent.
 */
internal fun deepEquals(ghostValue: Any?, jsonValue: Any?): Boolean {
    return when {
        ghostValue is Map<*, *> && jsonValue is Map<*, *> -> {
            val ghostKeys = ghostValue.keys.filterTo(mutableSetOf()) { it != GhostYamlConstants.STR_TAG_KEY }
            ghostKeys == jsonValue.keys &&
                ghostKeys.all { key -> deepEquals(ghostValue[key], jsonValue[key]) }
        }

        ghostValue is List<*> && jsonValue is List<*> ->
            ghostValue.size == jsonValue.size &&
                ghostValue.indices.all { i -> deepEquals(ghostValue[i], jsonValue[i]) }

        ghostValue is Number && jsonValue is Number -> ghostValue.toDouble() == jsonValue.toDouble()

        else -> ghostValue == jsonValue
    }
}

/**
 * True if parsing [case]'s YAML throws. Shared by [GhostYamlTestSuiteConformanceTest] and
 * `YamlComplianceReport`'s `main()` so the two can never quietly drift apart.
 */
internal fun parseThrew(case: YamlTestSuiteCase): Boolean {
    return try {
        GhostYamlFlatReader(case.inYamlBytes).readAllDocuments()
        false
    } catch (e: Exception) {
        true
    }
}

/** True if [case]'s decoded tree matches its `in.json` fixture. See [parseThrew]. */
internal fun valueMatches(case: YamlTestSuiteCase): Boolean {
    val ghostDocs = try {
        GhostYamlFlatReader(case.inYamlBytes).readAllDocuments()
    } catch (e: Exception) {
        return false
    }
    val jsonDocs = decodeJsonDocuments(case.inJsonText!!).map { normalize(it) }
    return ghostDocs.size == jsonDocs.size &&
        ghostDocs.indices.all { i -> deepEquals(ghostDocs[i], jsonDocs[i]) }
}
