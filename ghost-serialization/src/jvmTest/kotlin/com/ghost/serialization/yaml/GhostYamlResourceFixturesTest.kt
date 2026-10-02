package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM-only YAML fixture file tests (classpath resources under commonTest/resources). */
class GhostYamlResourceFixturesTest {

    @Test
    fun `parses spring_boot_app yaml dataset without crash`() {
        val yaml = readResource(path = "yaml/spring_boot_app.yaml")
        val result = parseMap(yaml = yaml)
        assertTrue(actual = result.containsKey(key = "spring"))
        assertTrue(actual = result.containsKey(key = "server"))
        assertTrue(actual = result.containsKey(key = "management"))
        assertTrue(actual = result.containsKey(key = "logging"))
        assertTrue(actual = result.containsKey(key = "ghost"))
    }

    @Test
    fun `parses edge_multiline yaml dataset completely`() {
        val yaml = readResource(path = "yaml/edge_multiline.yaml")
        val result = parseMap(yaml = yaml)
        assertEquals(
            expected = "Line one\nLine two\nLine three\n",
            actual = result["literal_block"]
        )
        assertEquals(
            expected = "Line one\nLine two\nLine three",
            actual = result["literal_strip"]
        )
        assertEquals(
            expected = "Line one\nLine two\nLine three\n\n\n",
            actual = result["literal_keep"]
        )
        assertEquals(
            expected = "This is the first paragraph which gets folded into one line.\nThis is a second paragraph after a blank line.\n",
            actual = result["folded_block"]
        )
        assertEquals(
            expected = "This line is folded and trailing newlines stripped",
            actual = result["folded_strip"]
        )
        assertEquals(
            expected = "This line is folded with trailing newlines kept\n\n\n",
            actual = result["folded_keep"]
        )
        assertEquals(
            expected = "This block starts at column 2\nand preserves relative indentation\n  inner indent here\n",
            actual = result["indented_2"]
        )
        assertEquals(
            expected = "This block starts at column 4\nand preserves relative indentation\n",
            actual = result["indented_4"]
        )
        assertEquals(
            expected = "",
            actual = result["empty_literal"]
        )
        assertEquals(
            expected = "only one line\n",
            actual = result["single_newline"]
        )
    }

    @Test
    fun `parses edge_flow_style yaml dataset completely`() {
        val yaml = readResource(path = "yaml/edge_flow_style.yaml")
        val result = parseMap(yaml = yaml)

        @Suppress("UNCHECKED_CAST")
        val simpleMap = result["simple_flow_map"] as Map<String, Any?>
        assertEquals(
            expected = "Alice",
            actual = simpleMap["name"]
        )
        assertEquals(
            expected = 30L,
            actual = simpleMap["age"]
        )
        assertEquals(
            expected = true,
            actual = simpleMap["active"]
        )

        @Suppress("UNCHECKED_CAST")
        val simpleSeq = result["simple_flow_seq"] as List<Any?>
        assertEquals(
            expected = 5,
            actual = simpleSeq.size
        )
        assertEquals(
            expected = "one",
            actual = simpleSeq[0]
        )
        assertEquals(
            expected = "five",
            actual = simpleSeq[4]
        )

        @Suppress("UNCHECKED_CAST")
        val intSeq = result["int_sequence"] as List<Any?>
        assertEquals(
            expected = 7,
            actual = intSeq.size
        )
        assertEquals(
            expected = 1L,
            actual = intSeq[0]
        )
        assertEquals(
            expected = 999L,
            actual = intSeq[6]
        )

        @Suppress("UNCHECKED_CAST")
        val mixedSeq = result["mixed_seq"] as List<Any?>
        assertEquals(
            expected = "hello",
            actual = mixedSeq[0]
        )
        assertEquals(
            expected = 42L,
            actual = mixedSeq[1]
        )
        assertEquals(
            expected = true,
            actual = mixedSeq[2]
        )
        assertEquals(
            expected = 3.14,
            actual = mixedSeq[3]
        )
        assertNull(actual = mixedSeq[4])

        @Suppress("UNCHECKED_CAST")
        val nestedFlow = result["nested_flow"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val user = nestedFlow["user"] as Map<String, Any?>
        assertEquals(
            expected = "Bob",
            actual = user["name"]
        )
        assertEquals(
            expected = "admin",
            actual = user["role"]
        )

        @Suppress("UNCHECKED_CAST")
        val server = result["server"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val options = server["options"] as Map<String, Any?>
        assertEquals(
            expected = false,
            actual = options["ssl"]
        )
        assertEquals(
            expected = 100L,
            actual = options["maxConnections"]
        )

        @Suppress("UNCHECKED_CAST")
        val allowedMethods = result["allowed_methods"] as List<Any?>
        assertEquals(
            expected = 6,
            actual = allowedMethods.size
        )
        assertEquals(
            expected = "GET",
            actual = allowedMethods[0]
        )
        assertEquals(
            expected = "OPTIONS",
            actual = allowedMethods[5]
        )

        @Suppress("UNCHECKED_CAST")
        val users = result["users"] as List<Any?>
        assertEquals(
            expected = 3,
            actual = users.size
        )
        @Suppress("UNCHECKED_CAST")
        val alice = users[0] as Map<String, Any?>
        assertEquals(
            expected = 1L,
            actual = alice["id"]
        )
        assertEquals(
            expected = "Alice",
            actual = alice["name"]
        )

        @Suppress("UNCHECKED_CAST")
        val matrix = result["matrix"] as List<Any?>
        assertEquals(
            expected = 3,
            actual = matrix.size
        )
        @Suppress("UNCHECKED_CAST")
        val row0 = matrix[0] as List<Any?>
        assertEquals(
            expected = 1L,
            actual = row0[0]
        )

        @Suppress("UNCHECKED_CAST")
        val quotedFlow = result["quoted_flow"] as Map<String, Any?>
        assertEquals(
            expected = "Hello, World!",
            actual = quotedFlow["message"]
        )
        assertEquals(
            expected = "/usr/local/bin",
            actual = quotedFlow["path"]
        )

        @Suppress("UNCHECKED_CAST")
        val nullableFlow = result["nullable_flow"] as Map<String, Any?>
        assertNull(actual = nullableFlow["email"])
        assertEquals(
            expected = 28L,
            actual = nullableFlow["age"]
        )

        @Suppress("UNCHECKED_CAST")
        val complex = result["complex"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val metadata = complex["metadata"] as Map<String, Any?>
        assertEquals(
            expected = "2024-01-15",
            actual = metadata["created"]
        )
        @Suppress("UNCHECKED_CAST")
        val tags = metadata["tags"] as List<Any?>
        assertEquals(
            expected = 3,
            actual = tags.size
        )
        assertEquals(
            expected = "yaml",
            actual = tags[0]
        )
    }

    @Test
    fun `parses openapi_schema yaml dataset completely`() {
        val yaml = readResource(path = "yaml/openapi_schema.yaml")
        val result = parseMap(yaml = yaml)
        assertEquals(
            expected = "3.0.3",
            actual = result["openapi"]
        )

        @Suppress("UNCHECKED_CAST")
        val info = result["info"] as Map<String, Any?>
        assertEquals(
            expected = "Ghost Serializer API",
            actual = info["title"]
        )

        @Suppress("UNCHECKED_CAST")
        val paths = result["paths"] as Map<String, Any?>
        assertTrue(actual = paths.containsKey(key = "/users"))

        @Suppress("UNCHECKED_CAST")
        val security = result["security"] as List<Any?>
        assertEquals(
            expected = 1,
            actual = security.size
        )
        @Suppress("UNCHECKED_CAST")
        val secMap = security[0] as Map<String, Any?>
        assertTrue(actual = secMap.containsKey(key = "bearerAuth"))
    }

    @Test
    fun `parses edge_explicit_tags yaml dataset completely`() {
        val yaml = readResource(path = "yaml/edge_explicit_tags.yaml")
        val result = parseMap(yaml = yaml)

        assertEquals(
            expected = "8080",
            actual = result["port_as_string"]
        )
        assertEquals(
            expected = "1.2.3",
            actual = result["version_as_string"]
        )
        assertEquals(
            expected = "true",
            actual = result["boolean_as_string"]
        )
        assertEquals(
            expected = "null",
            actual = result["null_as_string"]
        ) // !!str forces a string per YAML 1.2, not the null scalar
        assertEquals(
            expected = "0xFF",
            actual = result["hex_as_string"]
        )

        assertEquals(
            expected = 42L,
            actual = result["explicit_int"]
        )
        assertEquals(
            expected = 255L,
            actual = result["hex_int"]
        )
        assertEquals(
            expected = 15L,
            actual = result["octal_int"]
        )
        assertEquals(
            expected = 10L,
            actual = result["binary_int"]
        )

        assertEquals(
            expected = 3.14,
            actual = result["explicit_float"]
        )
        assertEquals(
            expected = 1.5e10,
            actual = result["sci_float"]
        )
        assertEquals(
            expected = Double.POSITIVE_INFINITY,
            actual = result["infinity_pos"]
        )
        assertEquals(
            expected = Double.NEGATIVE_INFINITY,
            actual = result["infinity_neg"]
        )
        assertTrue(actual = (result["not_a_number"] as Double).isNaN())

        assertEquals(
            expected = true,
            actual = result["explicit_true"]
        )
        assertEquals(
            expected = false,
            actual = result["explicit_false"]
        )

        assertNull(actual = result["explicit_null"])
        assertNull(actual = result["explicit_null2"])

        assertEquals(
            expected = "2024-01-15",
            actual = result["date_simple"]
        )
        assertEquals(
            expected = "2024-01-15T10:30:00Z",
            actual = result["date_with_time"]
        )

        @Suppress("UNCHECKED_CAST")
        val seq = result["explicit_seq"] as List<Any?>
        assertEquals(
            expected = "item1",
            actual = seq[0]
        )

        @Suppress("UNCHECKED_CAST")
        val map = result["explicit_map"] as Map<String, Any?>
        assertEquals(
            expected = "value1",
            actual = map["key1"]
        )
    }

    @Test
    fun `parses edge_anchors yaml dataset completely`() {
        val yaml = readResource(path = "yaml/edge_anchors.yaml")
        val result = parseMap(yaml = yaml)

        @Suppress("UNCHECKED_CAST")
        val baseConfig = result["base_config"] as Map<String, Any?>
        assertEquals(
            expected = 30L,
            actual = baseConfig["timeout"]
        )
        assertEquals(
            expected = 3L,
            actual = baseConfig["retries"]
        )
        assertEquals(
            expected = "INFO",
            actual = baseConfig["log_level"]
        )

        @Suppress("UNCHECKED_CAST")
        val serviceA = result["service_a"] as Map<String, Any?>
        assertEquals(
            expected = 30L,
            actual = serviceA["timeout"]
        )
        assertEquals(
            expected = "service-a",
            actual = serviceA["name"]
        )
        assertEquals(
            expected = 8080L,
            actual = serviceA["port"]
        )

        @Suppress("UNCHECKED_CAST")
        val serviceB = result["service_b"] as Map<String, Any?>
        assertEquals(
            expected = 60L,
            actual = serviceB["timeout"]
        ) // overridden
        assertEquals(
            expected = "service-b",
            actual = serviceB["name"]
        )
        assertEquals(
            expected = 8081L,
            actual = serviceB["port"]
        )

        assertEquals(
            expected = "localhost",
            actual = result["default_host"]
        )
        assertEquals(
            expected = "localhost",
            actual = result["db_host"]
        )
        assertEquals(
            expected = "localhost",
            actual = result["cache_host"]
        )

        @Suppress("UNCHECKED_CAST")
        val projectA = result["project_a"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val tagsA = projectA["tags"] as List<Any?>
        assertEquals(
            expected = 3,
            actual = tagsA.size
        )
        assertEquals(
            expected = "kotlin",
            actual = tagsA[0]
        )

        // Multi-key merge (<<: [*defaults, *prod]): earlier sources win, so defaults'
        // log_level/max_connections survive over prod's.
        @Suppress("UNCHECKED_CAST")
        val serviceProd = result["service_prod"] as Map<String, Any?>
        assertEquals(
            expected = true,
            actual = serviceProd["enabled"]
        )
        assertEquals(
            expected = "WARN",
            actual = serviceProd["log_level"]
        )
        assertEquals(
            expected = 10L,
            actual = serviceProd["max_connections"]
        )

        @Suppress("UNCHECKED_CAST")
        val databases = result["databases"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val replica = databases["replica"] as Map<String, Any?>
        assertEquals(
            expected = "db-replica.internal",
            actual = replica["host"]
        )
        @Suppress("UNCHECKED_CAST")
        val replicaPool = replica["pool"] as Map<String, Any?>
        assertEquals(
            expected = 5L,
            actual = replicaPool["min"]
        )
    }

    @Test
    fun `parses edge_polymorphism yaml dataset completely`() {
        val yaml = readResource(path = "yaml/edge_polymorphism.yaml")
        val result = parseMap(yaml = yaml)
        // 1. Tag-based polymorphism (shapes)
        @Suppress("UNCHECKED_CAST")
        val shapes = result["shapes"] as List<Map<String, Any?>>
        assertEquals(
            expected = 3,
            actual = shapes.size
        )

        val circle = shapes[0]
        assertEquals(
            expected = "Circle",
            actual = circle["_tag"]
        )
        assertEquals(
            expected = 5.0,
            actual = circle["radius"]
        )
        assertEquals(
            expected = "red",
            actual = circle["color"]
        )

        val rectangle = shapes[1]
        assertEquals(
            expected = "Rectangle",
            actual = rectangle["_tag"]
        )
        assertEquals(
            expected = 10.0,
            actual = rectangle["width"]
        )
        assertEquals(
            expected = 3.0,
            actual = rectangle["height"]
        )
        assertEquals(
            expected = "blue",
            actual = rectangle["color"]
        )

        // 2. Property-based polymorphism (animals)
        @Suppress("UNCHECKED_CAST")
        val animals = result["animals"] as List<Map<String, Any?>>
        assertEquals(
            expected = 3,
            actual = animals.size
        )

        val dog = animals[0]
        assertEquals(
            expected = "Dog",
            actual = dog["type"]
        )
        assertEquals(
            expected = "Rex",
            actual = dog["name"]
        )
        assertEquals(
            expected = "German Shepherd",
            actual = dog["breed"]
        )

        // 3. Mixed event sourcing with tags
        @Suppress("UNCHECKED_CAST")
        val events = result["events"] as List<Map<String, Any?>>
        assertEquals(
            expected = 3,
            actual = events.size
        )
        assertEquals(
            expected = "UserCreatedEvent",
            actual = events[0]["_tag"]
        )
        assertEquals(
            expected = "evt-001",
            actual = events[0]["eventId"]
        )

        // 4. Custom discriminator name
        @Suppress("UNCHECKED_CAST")
        val notifications = result["notifications"] as List<Map<String, Any?>>
        assertEquals(
            expected = 3,
            actual = notifications.size
        )
        assertEquals(
            expected = "EmailNotification",
            actual = notifications[0]["kind"]
        )

        // 5. Sealed class scenario
        @Suppress("UNCHECKED_CAST")
        val results = result["results"] as List<Map<String, Any?>>
        assertEquals(
            expected = 3,
            actual = results.size
        )
        assertEquals(
            expected = "Success",
            actual = results[0]["_tag"]
        )
        assertEquals(
            expected = 42L,
            actual = results[0]["value"]
        )
    }

    private fun parseMap(yaml: String): Map<String, Any?> {
        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
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
