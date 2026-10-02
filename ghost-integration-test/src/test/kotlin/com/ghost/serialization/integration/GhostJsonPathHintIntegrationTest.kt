package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.FlattenedModel
import com.ghost.serialization.integration.model.NamingModel
import com.ghost.serialization.integration.model.PathHintEnumHolder
import com.ghost.serialization.integration.model.PathHintInferredHolder
import com.ghost.serialization.integration.model.PathHintInferredPayload
import com.ghost.serialization.integration.model.PathHintNestedRoot
import com.ghost.serialization.integration.model.PathHintRequiredModel
import com.ghost.serialization.integration.model.PathHintResilientHolder
import com.ghost.serialization.integration.model.PathHintShape
import com.ghost.serialization.integration.model.PathHintShapeHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end DX checks for JSONPath + fix hints through KSP-generated serializers.
 */
@OptIn(InternalGhostApi::class)
class GhostJsonPathHintIntegrationTest {

    @Test
    fun missingRequiredFieldIncludesPathAndHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintRequiredModel>("""{"id":1}""")
        }
        assertEquals(expected = "$.name", actual = ex.path)
        assertTrue(actual = ex.message.contains("Required field 'name'"))
        assertNotNull(actual = ex.hint)
        assertTrue(actual = ex.message.contains("Hint:"))
    }

    @Test
    fun missingRequiredUsesWireNameFromGhostName() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<NamingModel>("""{"user_id":1,"is_active":true}""")
        }
        assertEquals(expected = "$.full_name", actual = ex.path)
        assertTrue(actual = ex.message.contains("full_name"))
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun unknownDiscriminatorIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintShapeHolder>(
                """{"shape":{"type":"Triangle","r":1.0}}"""
            )
        }
        assertTrue(actual = ex.message.contains("Unknown type discriminator"))
        assertNotNull(actual = ex.hint)
        assertTrue(actual = ex.hint!!.contains("GhostFallback") || ex.hint!!.contains("subclass"))
    }

    @Test
    fun missingDiscriminatorIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintShapeHolder>("""{"shape":{"r":1.0}}""")
        }
        assertTrue(actual = ex.message.contains("Missing discriminator"))
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun knownShapeStillDeserializes() {
        val decoded = Ghost.deserialize<PathHintShapeHolder>(
            """{"shape":{"type":"Circle","r":2.5}}"""
        )
        assertEquals(expected = PathHintShape.Circle(2.5), actual = decoded.shape)
    }

    @Test
    fun invalidEnumIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintEnumHolder>("""{"status":"Gamma"}""")
        }
        assertTrue(
            actual = ex.message.contains("Invalid enum") ||
                ex.message.contains("Unexpected enum index")
        )
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun deepNestedListElementPath() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintNestedRoot>(
                """{"user":{"addresses":[{"zip":1},{"zip":true}]}}"""
            )
        }
        assertEquals(expected = "$.user.addresses[1].zip", actual = ex.path)
    }

    @Test
    fun flattenPathOnTypeMismatch() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<FlattenedModel>(
                """{"id":1,"attributes":{"value":{"level":true},"status":"ok"}}"""
            )
        }
        assertEquals(expected = "$.attributes.value.level", actual = ex.path)
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun resilientFieldRecoversThenSiblingKeepsCleanPath() {
        val ok = Ghost.deserialize<PathHintResilientHolder>(
            """{"soft":"not-int","hard":7}"""
        )
        assertNull(actual = ok.soft)
        assertEquals(expected = 7, actual = ok.hard)

        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintResilientHolder>(
                """{"soft":"not-int","hard":true}"""
            )
        }
        assertEquals(expected = "$.hard", actual = ex.path)
    }

    @Test
    fun inferredNullRequiredFieldUsesMissingFieldPath() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintInferredPayload>("""{"code":null}""")
        }
        assertEquals(expected = "$.code", actual = ex.path)
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun inferredNestedNullRequiredFieldIncludesParentKey() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintInferredHolder>(
                """{"payload":{"code":null}}"""
            )
        }
        assertEquals(expected = "$.payload.code", actual = ex.path)
    }
}
