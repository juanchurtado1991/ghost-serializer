@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ApiEventDefault
import com.ghost.serialization.integration.model.ApiEventExplicitType
import com.ghost.serialization.integration.model.CustomAction
import com.ghost.serialization.integration.model.GhostDiscriminatorTestPayload
import com.ghost.serialization.integration.model.GhostKindEvent
import com.ghost.serialization.integration.model.JsonLdNode
import com.ghost.serialization.integration.model.StripeObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Custom sealed-class discriminator support: implicit and explicit `type`, custom keys such as
 * `kind`, `object`, and `@type`, error handling for missing or unknown discriminators, serialized
 * key placement, subclass round-trips, and mixed-discriminator payloads.
 */
class GhostCustomDiscriminatorTest {

    // ─── 1. Backward compatibility — default "type" implicit ─────────────────

    @Test
    fun `default implicit discriminator uses type field`() {
        val event: ApiEventDefault = ApiEventDefault.Login(userId = "u_001")
        val json = Ghost.serialize(event)

        assertContains(json, "\"type\"")
        assertContains(json, "\"Login\"")

        val decoded = Ghost.deserialize<ApiEventDefault>(json)
        assertEquals(expected = event, actual = decoded)
    }

    @Test
    fun `default implicit discriminator roundtrip all subclasses`() {
        val events: List<ApiEventDefault> = listOf(
            ApiEventDefault.Login(userId = "u_001"),
            ApiEventDefault.Logout(sessionId = "session_abc"),
        )
        events.forEach { event ->
            val json = Ghost.serialize(event)
            val decoded = Ghost.deserialize<ApiEventDefault>(json)
            assertEquals(expected = event, actual = decoded)
        }
    }

    // ─── 2. Explicit "type" == implicit "type" ────────────────────────────────

    @Test
    fun `explicit type discriminator produces identical JSON to implicit`() {
        // Both sealed classes have Login/Logout with userId/sessionId — same shape
        val implicit: ApiEventDefault = ApiEventDefault.Login(userId = "u_001")
        val explicit: ApiEventExplicitType = ApiEventExplicitType.Login(userId = "u_001")

        val jsonImplicit = Ghost.serialize(implicit)
        val jsonExplicit = Ghost.serialize(explicit)

        assertContains(jsonImplicit, "\"type\"")
        assertContains(jsonExplicit, "\"type\"")
        assertEquals(expected = jsonImplicit, actual = jsonExplicit)
    }

    // ─── 3. Custom "kind" discriminator ──────────────────────────────────────

    @Test
    fun `kind discriminator writes kind field in JSON`() {
        val event: GhostKindEvent = GhostKindEvent.Created(id = "e_1", name = "Ghost")
        val json = Ghost.serialize(event)

        assertContains(json, "\"kind\"")
        assertContains(json, "\"Created\"")
        assertTrue(
            actual = !json.contains("\"type\""),
            message = "Should not contain 'type' when discriminator is 'kind'"
        )
    }

    @Test
    fun `kind discriminator roundtrip Created`() {
        val event: GhostKindEvent = GhostKindEvent.Created(id = "e_1", name = "Ghost")
        val json = Ghost.serialize(event)
        val decoded = Ghost.deserialize<GhostKindEvent>(json)
        assertEquals(expected = event, actual = decoded)
    }

    @Test
    fun `kind discriminator roundtrip Deleted`() {
        val event: GhostKindEvent = GhostKindEvent.Deleted(id = "e_2")
        val json = Ghost.serialize(event)
        val decoded = Ghost.deserialize<GhostKindEvent>(json)
        assertEquals(expected = event, actual = decoded)
    }

    @Test
    fun `kind discriminator roundtrip Updated`() {
        val event: GhostKindEvent = GhostKindEvent.Updated(id = "e_3", changes = "name->Ghost v2")
        val json = Ghost.serialize(event)
        val decoded = Ghost.deserialize<GhostKindEvent>(json)
        assertEquals(expected = event, actual = decoded)
    }

    @Test
    fun `kind discriminator can deserialize manually crafted JSON`() {
        val json = """{"kind":"Deleted","id":"e_99"}"""
        val decoded = Ghost.deserialize<GhostKindEvent>(json)
        assertEquals(expected = GhostKindEvent.Deleted(id = "e_99"), actual = decoded)
    }

    // ─── 4. Custom "object" discriminator (Stripe-style) ─────────────────────

    @Test
    fun `object discriminator writes object field in JSON`() {
        val charge: StripeObject = StripeObject.Charge(amount = 2000L, currency = "usd")
        val json = Ghost.serialize(charge)

        assertContains(json, "\"object\"")
        assertContains(json, "\"Charge\"")
        assertTrue(
            actual = !json.contains("\"type\""),
            message = "Should not contain 'type' when discriminator is 'object'"
        )
    }

    @Test
    fun `object discriminator roundtrip Charge`() {
        val charge: StripeObject = StripeObject.Charge(amount = 2000L, currency = "usd")
        val json = Ghost.serialize(charge)
        val decoded = Ghost.deserialize<StripeObject>(json)
        assertEquals(expected = charge, actual = decoded)
    }

    @Test
    fun `object discriminator roundtrip Refund`() {
        val refund: StripeObject = StripeObject.Refund(chargeId = "ch_001", amount = 1000L)
        val json = Ghost.serialize(refund)
        val decoded = Ghost.deserialize<StripeObject>(json)
        assertEquals(expected = refund, actual = decoded)
    }

    @Test
    fun `object discriminator can deserialize manually crafted JSON`() {
        val json = """{"object":"Refund","chargeId":"ch_abc","amount":500}"""
        val decoded = Ghost.deserialize<StripeObject>(json)
        assertEquals(expected = StripeObject.Refund(chargeId = "ch_abc", amount = 500L), actual = decoded)
    }

    // ─── 5. Custom "@type" discriminator (JSON-LD style) ─────────────────────

    @Test
    fun `atType discriminator writes @type field in JSON`() {
        val node: JsonLdNode = JsonLdNode.Person(name = "Juan", email = "juan@ghost.dev")
        val json = Ghost.serialize(node)

        assertContains(json, "\"@type\"")
        assertContains(json, "\"Person\"")
    }

    @Test
    fun `atType discriminator roundtrip Person`() {
        val node: JsonLdNode = JsonLdNode.Person(name = "Juan", email = "juan@ghost.dev")
        val json = Ghost.serialize(node)
        val decoded = Ghost.deserialize<JsonLdNode>(json)
        assertEquals(expected = node, actual = decoded)
    }

    @Test
    fun `atType discriminator roundtrip Organization`() {
        val node: JsonLdNode =
            JsonLdNode.Organization(name = "Ghost Corp", url = "https://ghost.dev")
        val json = Ghost.serialize(node)
        val decoded = Ghost.deserialize<JsonLdNode>(json)
        assertEquals(expected = node, actual = decoded)
    }

    @Test
    fun `atType discriminator can deserialize manually crafted JSON`() {
        val json = """{"@type":"Organization","name":"JetBrains","url":"https://jetbrains.com"}"""
        val decoded = Ghost.deserialize<JsonLdNode>(json)
        assertEquals(
            expected = JsonLdNode.Organization(name = "JetBrains", url = "https://jetbrains.com"),
            actual = decoded
        )
    }

    // ─── 6. Missing discriminator field → throws ──────────────────────────────

    @Test
    fun `missing kind field throws not silently corrupts`() {
        val json = """{"id":"e_1","name":"Ghost"}"""
        assertFailsWith<Exception> {
            Ghost.deserialize<GhostKindEvent>(json)
        }
    }

    @Test
    fun `missing type field throws for default discriminator`() {
        val json = """{"userId":"u_001"}"""
        assertFailsWith<Exception> {
            Ghost.deserialize<ApiEventDefault>(json)
        }
    }

    @Test
    fun `missing object field throws for stripe-style discriminator`() {
        val json = """{"amount":2000,"currency":"usd"}"""
        assertFailsWith<Exception> {
            Ghost.deserialize<StripeObject>(json)
        }
    }

    // ─── 7. Unknown discriminator value → throws ──────────────────────────────

    @Test
    fun `unknown kind value throws`() {
        val json = """{"kind":"Archived","id":"e_1"}"""
        assertFailsWith<Exception> {
            Ghost.deserialize<GhostKindEvent>(json)
        }
    }

    @Test
    fun `unknown object value throws`() {
        val json = """{"object":"Subscription","amount":1000}"""
        assertFailsWith<Exception> {
            Ghost.deserialize<StripeObject>(json)
        }
    }

    // ─── 8. Discriminator key placement ───────────────────────────────────────

    @Test
    fun `kind discriminator key appears before other fields in JSON`() {
        // Ghost writes the discriminator as the first field — important for streaming parsers
        val json = Ghost.serialize(GhostKindEvent.Created(id = "e_1", name = "Ghost"))
        val kindIndex = json.indexOf("\"kind\"")
        val idIndex = json.indexOf("\"id\"")
        assertTrue(actual = kindIndex < idIndex, message = "Discriminator 'kind' should appear before payload fields")
    }

    @Test
    fun `object discriminator key appears before other fields in JSON`() {
        val json = Ghost.serialize(StripeObject.Charge(amount = 2000L, currency = "usd"))
        val objIndex = json.indexOf("\"object\"")
        val amountIndex = json.indexOf("\"amount\"")
        assertTrue(
            actual = objIndex < amountIndex,
            message = "Discriminator 'object' should appear before payload fields"
        )
    }

    // ─── 9. List of polymorphic objects with custom discriminator ─────────────

    @Test
    fun `list of kind events roundtrips correctly`() {
        val events: List<GhostKindEvent> = listOf(
            GhostKindEvent.Created(id = "e_1", name = "Ghost"),
            GhostKindEvent.Deleted(id = "e_2"),
            GhostKindEvent.Updated(id = "e_3", changes = "v2")
        )
        // Per-element, not list-level — Ghost only serializes single model roots; a list needs
        // a wrapping data class with @GhostSerialization.
        events.forEach { event ->
            val singleJson = Ghost.serialize(event)
            val decoded = Ghost.deserialize<GhostKindEvent>(singleJson)
            assertEquals(expected = event, actual = decoded)
        }
        events.forEach { event ->
            val json = Ghost.serialize(event)
            assertContains(json, "\"kind\"")
            assertTrue(actual = !json.contains("\"type\""))
        }
    }

    // ─── 10. Composed payload with mixed discriminators ───────────────────────

    @Test
    fun `composed payload with multiple discriminators roundtrips correctly`() {
        val payload = GhostDiscriminatorTestPayload(
            defaultEvent = ApiEventDefault.Login(userId = "u_001"),
            kindEvent = GhostKindEvent.Created(id = "e_1", name = "Ghost"),
            stripeObject = StripeObject.Charge(amount = 2000L, currency = "usd")
        )
        val json = Ghost.serialize(payload)

        assertContains(json, "\"type\"")    // from ApiEventDefault
        assertContains(json, "\"kind\"")    // from GhostKindEvent
        assertContains(json, "\"object\"")  // from StripeObject

        val decoded = Ghost.deserialize<GhostDiscriminatorTestPayload>(json)
        assertEquals(expected = payload, actual = decoded)
    }

    @Test
    fun `non-nested sealed subclass roundtrips correctly`() {
        val event: ApiEventDefault = CustomAction(actionName = "click")
        val json = Ghost.serialize(event)

        assertContains(json, "\"type\"")
        assertContains(json, "\"CustomAction\"")

        val decoded = Ghost.deserialize<ApiEventDefault>(json)
        assertEquals(expected = event, actual = decoded)
    }
}
