package com.ghost.serialization.yaml

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

    @Test
    fun `parses edge_anchors yaml dataset completely`() {
        val yaml = loadTestResource("yaml/edge_anchors.yaml")
        val result = parseMap(yaml)

        // base_config
        @Suppress("UNCHECKED_CAST")
        val baseConfig = result["base_config"] as Map<String, Any?>
        assertEquals(30L, baseConfig["timeout"])
        assertEquals(3L, baseConfig["retries"])
        assertEquals("INFO", baseConfig["log_level"])

        // service_a
        @Suppress("UNCHECKED_CAST")
        val serviceA = result["service_a"] as Map<String, Any?>
        assertEquals(30L, serviceA["timeout"])
        assertEquals("service-a", serviceA["name"])
        assertEquals(8080L, serviceA["port"])

        // service_b
        @Suppress("UNCHECKED_CAST")
        val serviceB = result["service_b"] as Map<String, Any?>
        assertEquals(60L, serviceB["timeout"]) // overridden
        assertEquals("service-b", serviceB["name"])
        assertEquals(8081L, serviceB["port"])

        // default_host
        assertEquals("localhost", result["default_host"])
        assertEquals("localhost", result["db_host"])
        assertEquals("localhost", result["cache_host"])

        // project_a
        @Suppress("UNCHECKED_CAST")
        val projectA = result["project_a"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val tagsA = projectA["tags"] as List<Any?>
        assertEquals(3, tagsA.size)
        assertEquals("kotlin", tagsA[0])

        // service_prod (multiple merge)
        @Suppress("UNCHECKED_CAST")
        val serviceProd = result["service_prod"] as Map<String, Any?>
        assertEquals(true, serviceProd["enabled"])
        // Wait, standard YAML resolution in a merge list:
        // defaults is &defaults (enabled: true, log_level: WARN, max_connections: 10)
        // prod is &prod (log_level: ERROR, max_connections: 100)
        // merge order: defaults then prod. The first occurrences in the merge list take precedence.
        // So log_level from defaults (WARN) or prod (ERROR)?
        // If we merge defaults then prod:
        // map starts empty. For each map in sequence:
        // add keys that are not present.
        // So defaults is merged first: map gets (enabled -> true, log_level -> WARN, max_connections -> 10).
        // Then prod is merged: log_level and max_connections are already in map, so they are not overwritten.
        // So log_level is WARN, max_connections is 10.
        // Wait, let's verify if the test expects WARN or ERROR depending on spec.
        // Let's assert what service_prod should contain based on standard YAML or custom override.
        assertEquals("WARN", serviceProd["log_level"])
        assertEquals(10L, serviceProd["max_connections"])

        // database_defaults
        @Suppress("UNCHECKED_CAST")
        val databases = result["databases"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val replica = databases["replica"] as Map<String, Any?>
        assertEquals("db-replica.internal", replica["host"])
        @Suppress("UNCHECKED_CAST")
        val replicaPool = replica["pool"] as Map<String, Any?>
        assertEquals(5L, replicaPool["min"])
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

    private fun loadTestResource(path: String): String {
        return readTestResource(path)
    }
}
