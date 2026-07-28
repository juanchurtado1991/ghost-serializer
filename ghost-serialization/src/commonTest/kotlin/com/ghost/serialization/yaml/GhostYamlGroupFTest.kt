package com.ghost.serialization.yaml
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD — Group F & G Tests (Red phase)
 *
 * Tests for: Polymorphism (Tag-based, Property-based) and Directives (%YAML, %TAG).
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
