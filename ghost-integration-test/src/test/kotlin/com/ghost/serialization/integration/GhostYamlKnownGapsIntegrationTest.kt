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

        assertEquals(expected = value, actual = Ghost.decodeFromYaml<YamlShardCounter>(yaml))
        val encoded = Ghost.encodeToYaml(value = value)
        assertTrue(encoded.contains("\"18446744073709551615\""), encoded)
        assertEquals(expected = value, actual = Ghost.decodeFromYaml<YamlShardCounter>(encoded))
    }

    @Test
    fun plainULongBareNumberWithinLongRange() {
        val yaml = """shard_id: 9223372036854775807"""
        assertEquals(expected = YamlShardCounter(shard_id = 9223372036854775807uL), actual = Ghost.decodeFromYaml(yaml))
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
        assertEquals(expected = 2, actual = parsed.size)
        assertEquals(expected = "alpha", actual = parsed[0].name)
        assertEquals(expected = "beta", actual = parsed[1].name)
    }

    @Test
    fun encodeAllToYamlJoinsDocumentsWithSeparator() {
        val users = listOf(
            YamlBenchUser(id = 1, name = "one", email = "1@test", score = 1.0),
            YamlBenchUser(id = 2, name = "two", email = "2@test", score = 2.0),
        )
        val encoded = Ghost.encodeAllToYaml(values = users)
        val restored = Ghost.decodeAllFromYaml<YamlBenchUser>(encoded)
        assertEquals(expected = users, actual = restored)
    }

    @Test
    fun decodeAllFromYamlReturnsEmptyListForEmptyInput() {
        assertEquals(expected = emptyList(), actual = Ghost.decodeAllFromYaml<YamlBenchUser>(""))
    }
}
