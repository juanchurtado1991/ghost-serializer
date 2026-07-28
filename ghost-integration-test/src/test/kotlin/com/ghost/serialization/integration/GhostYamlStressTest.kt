package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeAllFromYaml
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeAllToYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.integration.model.YamlBenchUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Larger and messier YAML payloads for integration hardening beyond known-gap fixtures. */
class GhostYamlStressTest {

    @Test
    fun largeTeamDocumentRoundTrips() {
        val users = (1..50).map { id ->
            YamlBenchUser(
                id = id,
                name = "user-$id",
                email = "user$id@test.local",
                score = id * 1.5,
                isActive = id % 2 == 0,
                role = if (id % 3 == 0) "ADMIN" else "VIEWER",
                bio = "Bio line for user $id with emoji 🚀 and \"quotes\"",
            )
        }
        val encoded = Ghost.encodeAllToYaml(users)
        val restored = Ghost.decodeAllFromYaml<YamlBenchUser>(encoded)
        assertEquals(users, restored)
        assertTrue(encoded.length > 5_000, "Expected a large multi-doc payload")
    }

    @Test
    fun messyScalarPayloadRoundTrips() {
        val yaml = """
            id: 99
            name: "O'Brien: \"Captain\""
            email: "weird@test\n.local"
            score: -0.0
            isActive: false
            role: "CUSTOM:ROLE"
            bio: |
              line one
              line two with tab	here
        """.trimIndent()

        val parsed = Ghost.decodeFromYaml<YamlBenchUser>(yaml)
        assertEquals(99, parsed.id)
        assertTrue(parsed.name.contains("Captain"))
        assertTrue(parsed.bio!!.contains("line two"))

        val encoded = Ghost.encodeToYaml(parsed)
        val roundTrip = Ghost.decodeFromYaml<YamlBenchUser>(encoded)
        assertEquals(parsed.copy(bio = roundTrip.bio), roundTrip)
    }

    @Test
    fun repeatedDecodeEncodeStaysStable() {
        val yaml = """
            id: 1
            name: stable
            email: s@test
            score: 1.0
        """.trimIndent()
        var current = Ghost.decodeFromYaml<YamlBenchUser>(yaml)
        repeat(25) {
            current = Ghost.decodeFromYaml(Ghost.encodeToYaml(current))
        }
        assertEquals("stable", current.name)
        assertEquals(1, current.id)
    }

    @Test
    fun multiDocumentWithLeadingAndTrailingSeparators() {
        val yaml = """
            ---
            id: 1
            name: first
            email: 1@test
            score: 1.0
            ---
            id: 2
            name: second
            email: 2@test
            score: 2.0
            ---
        """.trimIndent()
        val parsed = Ghost.decodeAllFromYaml<YamlBenchUser>(yaml)
        assertEquals(2, parsed.size)
        assertEquals("first", parsed[0].name)
        assertEquals("second", parsed[1].name)
    }
}
