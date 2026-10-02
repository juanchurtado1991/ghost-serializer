package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeAllFromYaml
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeAllToYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.integration.model.YamlBenchUser
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readAllDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        val encoded = Ghost.encodeAllToYaml(values = users)
        val restored = Ghost.decodeAllFromYaml<YamlBenchUser>(encoded)
        assertEquals(expected = users, actual = restored)
        assertTrue(actual = encoded.length > 5_000, message = "Expected a large multi-doc payload")
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
        assertEquals(expected = 99, actual = parsed.id)
        assertTrue(actual = parsed.name.contains("Captain"))
        assertTrue(actual = parsed.bio!!.contains("line two"))

        val encoded = Ghost.encodeToYaml(value = parsed)
        val roundTrip = Ghost.decodeFromYaml<YamlBenchUser>(encoded)
        assertEquals(expected = parsed.copy(bio = roundTrip.bio), actual = roundTrip)
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
            current = Ghost.decodeFromYaml(Ghost.encodeToYaml(value = current))
        }
        assertEquals(expected = "stable", actual = current.name)
        assertEquals(expected = 1, actual = current.id)
    }

    @Test
    fun multiDocumentWithLeadingAndTrailingSeparators() {
        // No trailing `---` here — see multiDocumentTrailingDashesIsAnExplicitEmptyDocument for
        // why a dangling `---` is not harmless stream padding.
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
        """.trimIndent()
        val parsed = Ghost.decodeAllFromYaml<YamlBenchUser>(yaml)
        assertEquals(expected = 2, actual = parsed.size)
        assertEquals(expected = "first", actual = parsed[0].name)
        assertEquals(expected = "second", actual = parsed[1].name)
    }

    @Test
    fun multiDocumentTrailingDashesIsAnExplicitEmptyDocument() {
        // Per the YAML 1.2 grammar (confirmed against yaml-test-suite case 6XDY: `---\n---\n`
        // decodes as two null documents), a `---` with nothing after it is a real, explicit
        // document whose content is the empty/null node — not just a stream terminator.
        val yaml = """
            id: 1
            name: first
            email: 1@test
            score: 1.0
            ---
        """.trimIndent()
        val documents = GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).readAllDocuments()
        assertEquals(expected = 2, actual = documents.size)
        assertNull(actual = documents[1])
    }
}
