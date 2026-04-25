package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

// ─── Existing models ──────────────────────────────────────────────────────────

@JvmInline
@GhostSerialization
value class GhostUserToken(val value: String)

@GhostSerialization
sealed class GhostShape {
    @GhostSerialization
    data class Circle(val radius: Double) : GhostShape()

    @GhostSerialization
    data class Square(val side: Double) : GhostShape()
}

@GhostSerialization
data class GhostAdvancedProfile(
    val token: GhostUserToken,
    val shapes: List<GhostShape>
)

// ─── Discriminator: default "type" (implicit) ────────────────────────────────
// Verifies backward compatibility — omitting discriminator uses "type" by default.

@GhostSerialization
sealed class ApiEventDefault {
    @GhostSerialization
    data class Login(val userId: String) : ApiEventDefault()
    @GhostSerialization
    data class Logout(val sessionId: String) : ApiEventDefault()
}

// ─── Discriminator: default "type" (explicit) ────────────────────────────────
// Same behavior, but explicitly declared — both must produce identical JSON.

@GhostSerialization(discriminator = "type")
sealed class ApiEventExplicitType {
    @GhostSerialization
    data class Login(val userId: String) : ApiEventExplicitType()
    @GhostSerialization
    data class Logout(val sessionId: String) : ApiEventExplicitType()
}

// ─── Discriminator: "kind" ────────────────────────────────────────────────────
// Common in Google APIs, Kubernetes, and internal platforms.

@GhostSerialization(discriminator = "kind")
sealed class GhostKindEvent {
    @GhostSerialization
    data class Created(val id: String, val name: String) : GhostKindEvent()
    @GhostSerialization
    data class Deleted(val id: String) : GhostKindEvent()
    @GhostSerialization
    data class Updated(val id: String, val changes: String) : GhostKindEvent()
}

// ─── Discriminator: "object" ──────────────────────────────────────────────────
// Stripe API convention: {"object": "charge", "amount": 100, ...}

@GhostSerialization(discriminator = "object")
sealed class StripeObject {
    @GhostSerialization
    data class Charge(val amount: Long, val currency: String) : StripeObject()
    @GhostSerialization
    data class Refund(val chargeId: String, val amount: Long) : StripeObject()
}

// ─── Discriminator: "@type" ───────────────────────────────────────────────────
// JSON-LD / schema.org convention: {"@type": "Person", "name": "..."}

@GhostSerialization(discriminator = "@type")
sealed class JsonLdNode {
    @GhostSerialization
    data class Person(val name: String, val email: String) : JsonLdNode()
    @GhostSerialization
    data class Organization(val name: String, val url: String) : JsonLdNode()
}

// ─── Composed payload with multiple discriminators ────────────────────────────

@GhostSerialization
data class GhostDiscriminatorTestPayload(
    val defaultEvent: ApiEventDefault,
    val kindEvent: GhostKindEvent,
    val stripeObject: StripeObject
)
@GhostSerialization
data class NestedGenericModel(
    val data: Map<String, List<Map<String, Int>>>
)

@GhostSerialization
data class EmojiKeyModel(
    val familyName: String,
    val rocketCount: Int,
    val emojiMap: Map<String, String> = emptyMap()
)

@GhostSerialization
data class OverlappingKeyModel(
    val id: Int,
    val id_internal: Int,
    val identity: String
)

@GhostSerialization
data class DecimalStress(
    val big: Double,
    val small: Float,
    val precise: Double
)
