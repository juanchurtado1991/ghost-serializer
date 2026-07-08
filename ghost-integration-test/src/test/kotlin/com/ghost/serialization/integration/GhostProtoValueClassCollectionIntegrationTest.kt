package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.ProtoAccountId
import com.ghost.serialization.integration.model.ProtoValueClassCollectionFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostProtoValueClassCollectionIntegrationTest {

    @Test
    fun serializesAndDeserializesValueClassCollectionsWithProtoCoercion() {
        val model = ProtoValueClassCollectionFixture(
            ids = listOf(ProtoAccountId(123L), ProtoAccountId(456L)),
            accounts = mapOf("alice" to ProtoAccountId(789L))
        )

        // 1. Serializar y certificar que los Longs están cotizados como Strings en el JSON
        val json = Ghost.encodeToString(model)
        
        // El formato de Proto3 JSON para int64/uint64 obliga a usar comillas: "123", "456", "789"
        assertTrue(json.contains("\"123\""), "Expected quoted 123 in JSON: $json")
        assertTrue(json.contains("\"456\""), "Expected quoted 456 in JSON: $json")
        assertTrue(json.contains("\"789\""), "Expected quoted 789 in JSON: $json")

        // 2. Deserializar y certificar que la estructura se reconstruye idéntica
        val deserialized = Ghost.deserialize<ProtoValueClassCollectionFixture>(json.encodeToByteArray())
        assertEquals(model, deserialized)
    }

    @Test
    fun deserializesFromBareNumbersLenientlyUnderProto() {
        // gRPC JSON mapping también acepta números sin comillas al deserializar (lenient parsing)
        val json = """{"ids":[123,456],"accounts":{"alice":789}}"""
        val deserialized = Ghost.deserialize<ProtoValueClassCollectionFixture>(json.encodeToByteArray())
        
        val expected = ProtoValueClassCollectionFixture(
            ids = listOf(ProtoAccountId(123L), ProtoAccountId(456L)),
            accounts = mapOf("alice" to ProtoAccountId(789L))
        )
        assertEquals(expected, deserialized)
    }
}
