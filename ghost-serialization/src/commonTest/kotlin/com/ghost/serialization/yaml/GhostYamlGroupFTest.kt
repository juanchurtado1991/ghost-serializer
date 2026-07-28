package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
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

        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())

        @Suppress("UNCHECKED_CAST")
        val doc = reader.readDocument() as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val shape = doc["shape"] as Map<String, Any?>
        assertEquals("!my-prefix-Circle", shape["_tag"])
        assertEquals(10L, shape["radius"])
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}
