package com.ghost.serialization.yaml
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * TDD — Group E Tests (Red phase)
 *
 * Tests for: Anchors (&anchor), Aliases (*alias), and Merge Keys (<<: *alias).
 */
class GhostYamlGroupETest {

    @Test
    fun `reads simple anchor and alias`() {
        val yaml = """
            base_config: &base
              timeout: 30
              retries: 3
            service_a:
              <<: *base
              name: service-a
              port: 8080
        """.trimIndent()
        val result = parseMap(yaml)
        
        @Suppress("UNCHECKED_CAST")
        val serviceA = result["service_a"] as Map<String, Any?>
        assertEquals(30L, serviceA["timeout"])
        assertEquals(3L, serviceA["retries"])
        assertEquals("service-a", serviceA["name"])
        assertEquals(8080L, serviceA["port"])
    }

    @Test
    fun `reads scalar anchor and alias`() {
        val yaml = """
            default_host: &default_host "localhost"
            db_host: *default_host
        """.trimIndent()
        val result = parseMap(yaml)
        assertEquals("localhost", result["default_host"])
        assertEquals("localhost", result["db_host"])
    }

    @Test
    fun `reads sequence anchor and alias`() {
        val yaml = """
            common_tags: &tags
              - kotlin
              - kmp
            project_a:
              name: ghost-serializer
              tags: *tags
        """.trimIndent()
        val result = parseMap(yaml)
        @Suppress("UNCHECKED_CAST")
        val tags = result["project_a"].let { it as Map<String, Any?> }["tags"] as List<Any?>
        assertEquals(2, tags.size)
        assertEquals("kotlin", tags[0])
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

}
