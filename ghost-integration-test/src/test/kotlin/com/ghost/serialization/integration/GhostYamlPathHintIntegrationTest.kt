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
        assertEquals("$.email", ex.path)
        assertTrue(ex.message.contains("Required field 'email'"))
        assertNotNull(ex.hint)
        assertTrue(ex.message.contains("Hint:"))
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
        assertEquals("$.id", ex.path)
        assertNotNull(ex.hint)
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
        assertEquals("$.name", ex.path)
        assertNotNull(ex.hint)
    }
}
