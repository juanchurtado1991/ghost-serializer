package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.integration.model.PathHintRequiredModel
import com.ghost.serialization.integration.model.YamlBenchUser
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
class GhostYamlPathHintIntegrationTest {

    @Test
    fun missingRequiredFieldIncludesPathAndHint() {
        val ex = assertFailsWith<GhostYamlException> {
            Ghost.decodeFromYaml<YamlBenchUser>(
                """
                id: 1
                name: Ada
                score: 1.5
                """.trimIndent()
            )
        }
        assertEquals(expected = "$.email", actual = ex.path)
        assertTrue(actual = ex.message.contains("Required field 'email'"))
        assertNotNull(actual = ex.hint)
        assertTrue(actual = ex.message.contains("Hint:"))
    }

    @Test
    fun typeMismatchAtFieldIncludesPath() {
        val ex = assertFailsWith<GhostYamlException> {
            Ghost.decodeFromYaml<YamlBenchUser>(
                """
                id:
                  - 1
                name: Ada
                email: a@b.c
                score: 1.5
                """.trimIndent()
            )
        }
        assertEquals(expected = "$.id", actual = ex.path)
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun yamlRequiredModelMissingName() {
        val ex = assertFailsWith<GhostYamlException> {
            Ghost.decodeFromYaml<PathHintRequiredModel>(
                """
                id: 7
                """.trimIndent()
            )
        }
        assertEquals(expected = "$.name", actual = ex.path)
        assertNotNull(actual = ex.hint)
    }
}
