package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Group F and G tests: tag-based and property-based polymorphism, and directives (`%YAML`, `%TAG`).
 */
class GhostYamlGroupFTest {

    @Test
    fun `parses document with YAML and TAG directives`() {
        val yaml = """
            %YAML 1.2
            %TAG !m! !my-prefix-
            ---
            shape: !m!Circle
              radius: 10
        """.trimIndent()

        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())

        @Suppress("UNCHECKED_CAST")
        val doc = reader.readDocument() as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val shape = doc["shape"] as Map<String, Any?>
        assertEquals(
            expected = "!my-prefix-Circle",
            actual = shape["_tag"]
        )
        assertEquals(
            expected = 10L,
            actual = shape["radius"]
        )
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}
