package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeAllFromYaml
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeAllToYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.integration.model.YamlBenchUser
import com.ghost.serialization.integration.model.YamlShardCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostYamlKnownGapsIntegrationTest {

    @Test
    fun plainULongFieldRoundTripsQuotedFullRange() {
        val value = YamlShardCounter(shard_id = ULong.MAX_VALUE)
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()

        assertEquals(value, Ghost.decodeFromYaml<YamlShardCounter>(yaml))
        val encoded = Ghost.encodeToYaml(value)
        assertTrue(encoded.contains("\"18446744073709551615\""), encoded)
        assertEquals(value, Ghost.decodeFromYaml<YamlShardCounter>(encoded))
    }

    @Test
    fun plainULongBareNumberWithinLongRange() {
        val yaml = """shard_id: 9223372036854775807"""
        assertEquals(YamlShardCounter(9223372036854775807uL), Ghost.decodeFromYaml(yaml))
    }

    @Test
    fun decodeAllFromYamlReadsMultipleDocuments() {
        val multiDoc = """
            id: 1
            name: alpha
            email: a@test
            score: 1.0
            ---
            id: 2
            name: beta
            email: b@test
            score: 2.0
        """.trimIndent()

        val parsed = Ghost.decodeAllFromYaml<YamlBenchUser>(multiDoc)
        assertEquals(2, parsed.size)
        assertEquals("alpha", parsed[0].name)
        assertEquals("beta", parsed[1].name)
    }

    @Test
    fun encodeAllToYamlJoinsDocumentsWithSeparator() {
        val users = listOf(
            YamlBenchUser(id = 1, name = "one", email = "1@test", score = 1.0),
            YamlBenchUser(id = 2, name = "two", email = "2@test", score = 2.0),
        )
        val encoded = Ghost.encodeAllToYaml(users)
        val restored = Ghost.decodeAllFromYaml<YamlBenchUser>(encoded)
        assertEquals(users, restored)
    }

    @Test
    fun decodeAllFromYamlReturnsEmptyListForEmptyInput() {
        assertEquals(emptyList(), Ghost.decodeAllFromYaml<YamlBenchUser>(""))
    }
}
