package com.ghost.serialization.yaml
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.yaml.writer.GhostYamlWriter

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
    fun `parses edge_polymorphism yaml dataset completely`() {
        val yaml = loadTestResource("yaml/edge_polymorphism.yaml")
        val result = parseMap(yaml)
        println("DEBUG RESULT: $result")

        // 1. Tag-based polymorphism (shapes)
        @Suppress("UNCHECKED_CAST")
        val shapes = result["shapes"] as List<Map<String, Any?>>
        assertEquals(3, shapes.size)

        val circle = shapes[0]
        assertEquals("Circle", circle["_tag"])
        assertEquals(5.0, circle["radius"])
        assertEquals("red", circle["color"])

        val rectangle = shapes[1]
        assertEquals("Rectangle", rectangle["_tag"])
        assertEquals(10.0, rectangle["width"])
        assertEquals(3.0, rectangle["height"])
        assertEquals("blue", rectangle["color"])

        // 2. Property-based polymorphism (animals)
        @Suppress("UNCHECKED_CAST")
        val animals = result["animals"] as List<Map<String, Any?>>
        assertEquals(3, animals.size)

        val dog = animals[0]
        assertEquals("Dog", dog["type"])
        assertEquals("Rex", dog["name"])
        assertEquals("German Shepherd", dog["breed"])

        // 3. Mixed event sourcing with tags
        @Suppress("UNCHECKED_CAST")
        val events = result["events"] as List<Map<String, Any?>>
        assertEquals(3, events.size)
        assertEquals("UserCreatedEvent", events[0]["_tag"])
        assertEquals("evt-001", events[0]["eventId"])

        // 4. Custom discriminator name
        @Suppress("UNCHECKED_CAST")
        val notifications = result["notifications"] as List<Map<String, Any?>>
        assertEquals(3, notifications.size)
        assertEquals("EmailNotification", notifications[0]["kind"])

        // 5. Sealed class scenario
        @Suppress("UNCHECKED_CAST")
        val results = result["results"] as List<Map<String, Any?>>
        assertEquals(3, results.size)
        assertEquals("Success", results[0]["_tag"])
        assertEquals(42L, results[0]["value"])
    }

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

    private fun loadTestResource(path: String): String {
        return readTestResource(path)
    }
}
