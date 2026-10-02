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
            ids = listOf(ProtoAccountId(value = 123L), ProtoAccountId(value = 456L)),
            accounts = mapOf("alice" to ProtoAccountId(value = 789L))
        )

        val json = Ghost.encodeToString(model)

        // Proto3 JSON requires int64/uint64 quoted as strings
        assertTrue(actual = json.contains("\"123\""), message = "Expected quoted 123 in JSON: $json")
        assertTrue(actual = json.contains("\"456\""), message = "Expected quoted 456 in JSON: $json")
        assertTrue(actual = json.contains("\"789\""), message = "Expected quoted 789 in JSON: $json")

        val deserialized =
            Ghost.deserialize<ProtoValueClassCollectionFixture>(json.encodeToByteArray())
        assertEquals(expected = model, actual = deserialized)
    }

    @Test
    fun deserializesFromBareNumbersLenientlyUnderProto() {
        // gRPC JSON mapping also accepts unquoted numbers on deserialize (lenient parsing)
        val json = """{"ids":[123,456],"accounts":{"alice":789}}"""
        val deserialized =
            Ghost.deserialize<ProtoValueClassCollectionFixture>(json.encodeToByteArray())

        val expected = ProtoValueClassCollectionFixture(
            ids = listOf(ProtoAccountId(value = 123L), ProtoAccountId(value = 456L)),
            accounts = mapOf("alice" to ProtoAccountId(value = 789L))
        )
        assertEquals(expected = expected, actual = deserialized)
    }
}
