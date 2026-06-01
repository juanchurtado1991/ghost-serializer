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
        assertNotNull(resource, "Could not find twitter_macro.json resource")
        val jsonString = resource.readText()
        println("Successfully read twitter_macro.json. Length: ${jsonString.length}")
        
        // 1. Decode using Kotlinx Serialization (Reference Parser)
        val kJson = Json { ignoreUnknownKeys = true }
        val referenceResponse = kJson.decodeFromString<TwitterResponse>(jsonString)
        
        // 2. Decode using Ghost
        val ghostResponse = Ghost.deserialize<TwitterResponse>(jsonString)
        
        // 3. Deep Structural Validation (Compare all fields/values parsed by both engines)
        assertEquals(
            referenceResponse, 
            ghostResponse, 
            "Data mismatch between Kotlinx Serialization and Ghost! Some values were parsed incorrectly or lost."
        )
        println("Deep validation passed! Ghost parsed 100% of the dataset structurally identical to Kotlinx.")

        // 4. Roundtrip Validation (Serialize with Ghost -> Deserialize with Ghost -> Verify Match)
        val serializedBytes = Ghost.encodeToBytes(ghostResponse)
        val deserializedRoundtrip = Ghost.deserialize<TwitterResponse>(serializedBytes)
        
        assertEquals(
            ghostResponse, 
            deserializedRoundtrip, 
            "Roundtrip data loss detected! Serialization -> Deserialization returned a mismatched object."
        )
        println("Roundtrip validation passed! Ghost serializes and deserializes the dataset with 0% data loss.")
    }

    @Test
    fun testTwitterSpecialFeatures() {
        val resource = this::class.java.classLoader.getResource("twitter_macro.json")
        assertNotNull(resource, "Could not find twitter_macro.json resource")
        val jsonString = resource.readText()

        // 1. Deserialize using special features model (Flatten, Ignore)
        println("Deserializing Twitter macro dataset using Ghost Special Features...")
        val response = Ghost.deserialize<TwitterSpecialResponse>(jsonString)
        
        // Assert that statuses were correctly parsed
        assertTrue(response.statuses.isNotEmpty(), "Statuses list should not be empty")
        
        // Validate first status (AYUMI)
        val firstTweet = response.statuses.first()
        assertEquals(505874924095815700L, firstTweet.id)
        
        // Verify GhostFlatten (user.screen_name -> screenName)
        assertEquals("ayuu0123", firstTweet.screenName, "GhostFlatten failed to extract nested screen_name correctly")
        
        // Verify GhostFlatten (metadata.result_type -> resultType)
        assertEquals("recent", firstTweet.resultType, "GhostFlatten failed to extract nested result_type correctly")
        
        // Verify GhostIgnore field has default value
        assertEquals("", firstTweet.source, "GhostIgnore failed; the field was populated when it should have been ignored")
        
        println("Deserialization and special features extraction successful!")

        // 2. Serialize special features model back using Ghost (tests GhostIgnore)
        println("Serializing special features model back to JSON bytes...")
        val serializedBytes = Ghost.encodeToBytes(response)
        val serializedJson = String(serializedBytes, Charsets.UTF_8)
        
        // Verify GhostIgnore worked: "source" field must NOT be in the serialized JSON
        assertTrue(!serializedJson.contains("\"source\":"), "GhostIgnore failed! Ignored property 'source' was found in the serialized JSON.")
        
        // 3. Deserialize back the serialized JSON to ensure 100% roundtrip capability
        println("Performing roundtrip deserialization on serialized special features JSON...")
        val roundtripResponse = Ghost.deserialize<TwitterSpecialResponse>(serializedBytes)
        
        // Verify roundtrip equivalence
        assertEquals(response, roundtripResponse, "Roundtrip comparison failed for Ghost Special Features model!")
        println("Ghost Special Features roundtrip validated successfully with 0% data loss!")
    }

    @Test
    fun testTwitterWrapFeature() {
        // 1. Create a wrapped model instance (simulating Twitter-like fields)
        val tweet = TwitterWrappedTweet(
            id = 505874924095815700L,
            text = "Hello Twitter Wrap!"
        )

        // 2. Serialize using Ghost
        println("Serializing TwitterWrappedTweet...")
        val serializedBytes = Ghost.encodeToBytes(tweet)
        val json = String(serializedBytes, Charsets.UTF_8)
        
        // Verify structural wrap: text must be nested under details -> text
        assertTrue(json.contains("\"details\":{\"text\":\"Hello Twitter Wrap!\"}"), "GhostWrap failed! Property was not correctly wrapped in the serialized JSON: $json")
        println("GhostWrap serialization validated successfully! Output: $json")

        // 3. Deserialize back using Ghost to verify roundtrip fidelity
        val deserialized = Ghost.deserialize<TwitterWrappedTweet>(serializedBytes)
        assertEquals(tweet, deserialized, "GhostWrap deserialization failed! Roundtrip object does not match original.")
        println("GhostWrap roundtrip deserialization validated successfully!")
    }
}
