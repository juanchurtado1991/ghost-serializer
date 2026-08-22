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
        assertEquals("$.name", ex.path)
        assertTrue(ex.message.contains("Required field 'name'"))
        assertNotNull(ex.hint)
        assertTrue(ex.message.contains("Hint:"))
    }

    @Test
    fun missingRequiredUsesWireNameFromGhostName() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<NamingModel>("""{"user_id":1,"is_active":true}""")
        }
        assertEquals("$.full_name", ex.path)
        assertTrue(ex.message.contains("full_name"))
        assertNotNull(ex.hint)
    }

    @Test
    fun unknownDiscriminatorIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintShapeHolder>(
                """{"shape":{"type":"Triangle","r":1.0}}"""
            )
        }
        assertTrue(ex.message.contains("Unknown type discriminator"))
        assertNotNull(ex.hint)
        assertTrue(ex.hint!!.contains("GhostFallback") || ex.hint!!.contains("subclass"))
    }

    @Test
    fun missingDiscriminatorIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintShapeHolder>("""{"shape":{"r":1.0}}""")
        }
        assertTrue(ex.message.contains("Missing discriminator"))
        assertNotNull(ex.hint)
    }

    @Test
    fun knownShapeStillDeserializes() {
        val decoded = Ghost.deserialize<PathHintShapeHolder>(
            """{"shape":{"type":"Circle","r":2.5}}"""
        )
        assertEquals(PathHintShape.Circle(2.5), decoded.shape)
    }

    @Test
    fun invalidEnumIncludesHint() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintEnumHolder>("""{"status":"Gamma"}""")
        }
        assertTrue(
            ex.message.contains("Invalid enum") ||
                ex.message.contains("Unexpected enum index")
        )
        assertNotNull(ex.hint)
    }

    @Test
    fun deepNestedListElementPath() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintNestedRoot>(
                """{"user":{"addresses":[{"zip":1},{"zip":true}]}}"""
            )
        }
        assertEquals("$.user.addresses[1].zip", ex.path)
    }

    @Test
    fun flattenPathOnTypeMismatch() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<FlattenedModel>(
                """{"id":1,"attributes":{"value":{"level":true},"status":"ok"}}"""
            )
        }
        assertEquals("$.attributes.value.level", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun resilientFieldRecoversThenSiblingKeepsCleanPath() {
        val ok = Ghost.deserialize<PathHintResilientHolder>(
            """{"soft":"not-int","hard":7}"""
        )
        assertNull(ok.soft)
        assertEquals(7, ok.hard)

        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintResilientHolder>(
                """{"soft":"not-int","hard":true}"""
            )
        }
        assertEquals("$.hard", ex.path)
    }

    @Test
    fun inferredNullRequiredFieldUsesMissingFieldPath() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintInferredPayload>("""{"code":null}""")
        }
        assertEquals("$.code", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun inferredNestedNullRequiredFieldIncludesParentKey() {
        val ex = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<PathHintInferredHolder>(
                """{"payload":{"code":null}}"""
            )
        }
        assertEquals("$.payload.code", ex.path)
    }
}
