package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.TwitterResponse
import com.ghost.serialization.integration.model.TwitterSpecialResponse
import com.ghost.serialization.integration.model.TwitterWrappedTweet
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostTwitterReproductionTest {

    @Test
    fun testTwitterDatasetDecoding() {
        val resource = this::class.java.classLoader.getResource("twitter_macro.json")
        assertNotNull(actual = resource, message = "Could not find twitter_macro.json resource")
        val jsonString = resource.readText()
        println("Successfully read twitter_macro.json. Length: ${jsonString.length}")

        val kJson = Json { ignoreUnknownKeys = true }
        val referenceResponse = kJson.decodeFromString<TwitterResponse>(jsonString)

        val ghostResponse = Ghost.deserialize<TwitterResponse>(jsonString)

        if (referenceResponse != ghostResponse) {
            println("MISMATCH DETECTED!")
            println("Reference status count: ${referenceResponse.statuses.size}")
            println("Ghost status count: ${ghostResponse.statuses.size}")
            val limit = minOf(referenceResponse.statuses.size, ghostResponse.statuses.size)
            for (i in 0 until limit) {
                val ref = referenceResponse.statuses[i]
                val gh = ghostResponse.statuses[i]
                if (ref != gh) {
                    println("First mismatch at index $i:")
                    println("  Ref: $ref")
                    println("  Ghost: $gh")
                    if (ref.text != gh.text) {
                        println("    Text Ref: ${ref.text}")
                        println("    Text Ghost: ${gh.text}")
                    }
                    if (ref.user != gh.user) {
                        println("    User Ref: ${ref.user}")
                        println("    User Ghost: ${gh.user}")
                    }
                    break
                }
            }
            assertEquals(expected = referenceResponse, actual = ghostResponse)
        }
        println("Deep validation passed! Ghost parsed 100% of the dataset structurally identical to Kotlinx.")

        val serializedBytes = Ghost.encodeToBytes(ghostResponse)
        val deserializedRoundtrip = Ghost.deserialize<TwitterResponse>(serializedBytes)

        assertEquals(
            expected = ghostResponse,
            actual = deserializedRoundtrip,
            message = "Roundtrip data loss detected! Serialization -> Deserialization returned a mismatched object."
        )
        println("Roundtrip validation passed! Ghost serializes and deserializes the dataset with 0% data loss.")
    }

    @Test
    fun testTwitterSpecialFeatures() {
        val resource = this::class.java.classLoader.getResource("twitter_macro.json")
        assertNotNull(actual = resource, message = "Could not find twitter_macro.json resource")
        val jsonString = resource.readText()

        println("Deserializing Twitter macro dataset using Ghost Special Features...")
        val response = Ghost.deserialize<TwitterSpecialResponse>(jsonString)

        assertTrue(actual = response.statuses.isNotEmpty(), message = "Statuses list should not be empty")

        val firstTweet = response.statuses.first()
        assertEquals(expected = 505874924095815700L, actual = firstTweet.id)

        // GhostFlatten: user.screen_name -> screenName
        assertEquals(
            expected = "ayuu0123",
            actual = firstTweet.screenName,
            message = "GhostFlatten failed to extract nested screen_name correctly"
        )

        // GhostFlatten: metadata.result_type -> resultType
        assertEquals(
            expected = "recent",
            actual = firstTweet.resultType,
            message = "GhostFlatten failed to extract nested result_type correctly"
        )

        assertEquals(
            expected = "",
            actual = firstTweet.source,
            message = "GhostIgnore failed; the field was populated when it should have been ignored"
        )

        println("Deserialization and special features extraction successful!")

        println("Serializing special features model back to JSON bytes...")
        val serializedBytes = Ghost.encodeToBytes(response)
        val serializedJson = String(serializedBytes, Charsets.UTF_8)

        assertTrue(
            actual = !serializedJson.contains("\"source\":"),
            message = "GhostIgnore failed! Ignored property 'source' was found in the serialized JSON."
        )

        println("Performing roundtrip deserialization on serialized special features JSON...")
        val roundtripResponse = Ghost.deserialize<TwitterSpecialResponse>(serializedBytes)

        assertEquals(
            expected = response,
            actual = roundtripResponse,
            message = "Roundtrip comparison failed for Ghost Special Features model!"
        )
        println("Ghost Special Features roundtrip validated successfully with 0% data loss!")
    }

    @Test
    fun testTwitterWrapFeature() {
        val tweet = TwitterWrappedTweet(
            id = 505874924095815700L,
            text = "Hello Twitter Wrap!"
        )

        println("Serializing TwitterWrappedTweet...")
        val serializedBytes = Ghost.encodeToBytes(tweet)
        val json = String(serializedBytes, Charsets.UTF_8)

        assertTrue(
            actual = json.contains("\"details\":{\"text\":\"Hello Twitter Wrap!\"}"),
            message = "GhostWrap failed! Property was not correctly wrapped in the serialized JSON: $json"
        )
        println("GhostWrap serialization validated successfully! Output: $json")

        val deserialized = Ghost.deserialize<TwitterWrappedTweet>(serializedBytes)
        assertEquals(
            expected = tweet,
            actual = deserialized,
            message = "GhostWrap deserialization failed! Roundtrip object does not match original."
        )
        println("GhostWrap roundtrip deserialization validated successfully!")
    }
}
