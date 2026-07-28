package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM-only YAML fixture file tests (classpath resources under commonTest/resources). */
class GhostYamlResourceFixturesTest {

    @Test
    fun `parses spring_boot_app yaml dataset without crash`() {
        val yaml = readResource("yaml/spring_boot_app.yaml")
        val result = parseMap(yaml)
        // Top-level keys must be present
        assertTrue(result.containsKey("spring"))
        assertTrue(result.containsKey("server"))
        assertTrue(result.containsKey("management"))
        assertTrue(result.containsKey("logging"))
        assertTrue(result.containsKey("ghost"))
    }
    @Test
    fun `parses edge_multiline yaml dataset completely`() {
        val yaml = readResource("yaml/edge_multiline.yaml")
        val result = parseMap(yaml)
        assertEquals("Line one\nLine two\nLine three\n", result["literal_block"])
        assertEquals("Line one\nLine two\nLine three", result["literal_strip"])
        assertEquals("Line one\nLine two\nLine three\n\n\n", result["literal_keep"])
        assertEquals(
            "This is the first paragraph which gets folded into one line.\nThis is a second paragraph after a blank line.\n",
            result["folded_block"]
        )
        assertEquals("This line is folded and trailing newlines stripped", result["folded_strip"])
        assertEquals("This line is folded with trailing newlines kept\n\n\n", result["folded_keep"])
        assertEquals(
            "This block starts at column 2\nand preserves relative indentation\n  inner indent here\n",
            result["indented_2"]
        )
        assertEquals(
            "This block starts at column 4\nand preserves relative indentation\n",
            result["indented_4"]
        )
        assertEquals("", result["empty_literal"])
        assertEquals("only one line\n", result["single_newline"])
    }
    @Test
    fun `parses edge_flow_style yaml dataset completely`() {
        val yaml = readResource("yaml/edge_flow_style.yaml")
        val result = parseMap(yaml)

        @Suppress("UNCHECKED_CAST")
        val simpleMap = result["simple_flow_map"] as Map<String, Any?>
        assertEquals("Alice", simpleMap["name"])
        assertEquals(30L, simpleMap["age"])
        assertEquals(true, simpleMap["active"])

        @Suppress("UNCHECKED_CAST")
        val simpleSeq = result["simple_flow_seq"] as List<Any?>
        assertEquals(5, simpleSeq.size)
        assertEquals("one", simpleSeq[0])
        assertEquals("five", simpleSeq[4])

        @Suppress("UNCHECKED_CAST")
        val intSeq = result["int_sequence"] as List<Any?>
        assertEquals(7, intSeq.size)
        assertEquals(1L, intSeq[0])
        assertEquals(999L, intSeq[6])

        @Suppress("UNCHECKED_CAST")
        val mixedSeq = result["mixed_seq"] as List<Any?>
        assertEquals("hello", mixedSeq[0])
        assertEquals(42L, mixedSeq[1])
        assertEquals(true, mixedSeq[2])
        assertEquals(3.14, mixedSeq[3])
        assertNull(mixedSeq[4])

        @Suppress("UNCHECKED_CAST")
        val nestedFlow = result["nested_flow"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val user = nestedFlow["user"] as Map<String, Any?>
        assertEquals("Bob", user["name"])
        assertEquals("admin", user["role"])

        @Suppress("UNCHECKED_CAST")
        val server = result["server"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val options = server["options"] as Map<String, Any?>
        assertEquals(false, options["ssl"])
        assertEquals(100L, options["maxConnections"])

        @Suppress("UNCHECKED_CAST")
        val allowedMethods = result["allowed_methods"] as List<Any?>
        assertEquals(6, allowedMethods.size)
        assertEquals("GET", allowedMethods[0])
        assertEquals("OPTIONS", allowedMethods[5])

        @Suppress("UNCHECKED_CAST")
        val users = result["users"] as List<Any?>
        assertEquals(3, users.size)
        @Suppress("UNCHECKED_CAST")
        val alice = users[0] as Map<String, Any?>
        assertEquals(1L, alice["id"])
        assertEquals("Alice", alice["name"])

        @Suppress("UNCHECKED_CAST")
        val matrix = result["matrix"] as List<Any?>
        assertEquals(3, matrix.size)
        @Suppress("UNCHECKED_CAST")
        val row0 = matrix[0] as List<Any?>
        assertEquals(1L, row0[0])

        @Suppress("UNCHECKED_CAST")
        val quotedFlow = result["quoted_flow"] as Map<String, Any?>
        assertEquals("Hello, World!", quotedFlow["message"])
        assertEquals("/usr/local/bin", quotedFlow["path"])

        @Suppress("UNCHECKED_CAST")
        val nullableFlow = result["nullable_flow"] as Map<String, Any?>
        assertNull(nullableFlow["email"])
        assertEquals(28L, nullableFlow["age"])

        @Suppress("UNCHECKED_CAST")
        val complex = result["complex"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val metadata = complex["metadata"] as Map<String, Any?>
        assertEquals("2024-01-15", metadata["created"])
        @Suppress("UNCHECKED_CAST")
        val tags = metadata["tags"] as List<Any?>
        assertEquals(3, tags.size)
        assertEquals("yaml", tags[0])
    }
    @Test
    fun `parses openapi_schema yaml dataset completely`() {
        val yaml = readResource("yaml/openapi_schema.yaml")
        val result = parseMap(yaml)
        assertEquals("3.0.3", result["openapi"])

        @Suppress("UNCHECKED_CAST")
        val info = result["info"] as Map<String, Any?>
        assertEquals("Ghost Serializer API", info["title"])

        @Suppress("UNCHECKED_CAST")
        val paths = result["paths"] as Map<String, Any?>
        assertTrue(paths.containsKey("/users"))

        @Suppress("UNCHECKED_CAST")
        val security = result["security"] as List<Any?>
        assertEquals(1, security.size)
        @Suppress("UNCHECKED_CAST")
        val secMap = security[0] as Map<String, Any?>
        assertTrue(secMap.containsKey("bearerAuth"))
    }
    @Test
    fun `parses edge_explicit_tags yaml dataset completely`() {
        val yaml = readResource("yaml/edge_explicit_tags.yaml")
        val result = parseMap(yaml)

        assertEquals("8080", result["port_as_string"])
        assertEquals("1.2.3", result["version_as_string"])
        assertEquals("true", result["boolean_as_string"])
        assertEquals("null", result["null_as_string"]) // !!str null is actually parsed as null or string "null"? Wait, in YAML 1.2, tag !!str forces scalar to be string. So it should be string "null".
        assertEquals("0xFF", result["hex_as_string"])

        assertEquals(42L, result["explicit_int"])
        assertEquals(255L, result["hex_int"])
        assertEquals(15L, result["octal_int"])
        assertEquals(10L, result["binary_int"])

        assertEquals(3.14, result["explicit_float"])
        assertEquals(1.5e10, result["sci_float"])
        assertEquals(Double.POSITIVE_INFINITY, result["infinity_pos"])
        assertEquals(Double.NEGATIVE_INFINITY, result["infinity_neg"])
        assertTrue((result["not_a_number"] as Double).isNaN())

        assertEquals(true, result["explicit_true"])
        assertEquals(false, result["explicit_false"])

        assertNull(result["explicit_null"])
        assertNull(result["explicit_null2"])

        assertEquals("2024-01-15", result["date_simple"])
        assertEquals("2024-01-15T10:30:00Z", result["date_with_time"])

        @Suppress("UNCHECKED_CAST")
        val seq = result["explicit_seq"] as List<Any?>
        assertEquals("item1", seq[0])

        @Suppress("UNCHECKED_CAST")
        val map = result["explicit_map"] as Map<String, Any?>
        assertEquals("value1", map["key1"])
    }
    @Test
    fun `parses edge_anchors yaml dataset completely`() {
        val yaml = readResource("yaml/edge_anchors.yaml")
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
    @Test
    fun `parses edge_polymorphism yaml dataset completely`() {
        val yaml = readResource("yaml/edge_polymorphism.yaml")
        val result = parseMap(yaml)
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
    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        @Suppress("UNCHECKED_CAST")
        return reader.readDocument() as Map<String, Any?>
    }

    private fun readResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: error("Test resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
