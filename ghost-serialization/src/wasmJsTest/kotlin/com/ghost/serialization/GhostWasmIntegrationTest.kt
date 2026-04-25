@file:Suppress("unused")
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ghost.serialization.benchmark.GhostCharacter
import com.ghost.serialization.generated.GhostAutoRegistry
import com.ghost.serialization.generated.GhostJsRegistryInitializer
import kotlin.js.JsAny

// Helpers to read the constructed JS Object natively from Wasm
private fun getNumberField(obj: JsAny, field: String): Int = js("obj[field]")
private fun getStringField(obj: JsAny, field: String): String = js("obj[field]")
private fun getObjectField(obj: JsAny, field: String): JsAny = js("obj[field]")
private fun isFieldNull(obj: JsAny, field: String): Boolean = js("obj[field] === null")
private fun isFieldUndefined(obj: JsAny, field: String): Boolean = js("obj[field] === undefined")
private fun isArray(obj: JsAny): Boolean = js("Array.isArray(obj)")
private fun getArrayLength(arr: JsAny): Int = js("arr.length")
private fun getArrayElement(arr: JsAny, index: Int): JsAny = js("arr[index]")

class GhostWasmIntegrationTest {

    private fun setupEnvironment() {
        try {
            GhostAutoRegistry.registerAll()
        } catch (_: Throwable) {}
        try {
            GhostJsRegistryInitializer.register()
        } catch (_: Throwable) {}
    }

    @Test
    fun testMetadataAvailability() {
        val qName = GhostCharacter::class.qualifiedName
        println(">>> [DEBUG] Qualified Name for GhostCharacter: $qName")
        assertNotNull(qName, "Qualified name should not be null in Wasm for data classes")
    }

    @Test
    fun testFullDeserializationPipeline() {
        setupEnvironment()
        val json = """{"id": 42, "name": "Rick", "status": "Alive", "species": "Human", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}, "image": "url"}"""
        val result = ghostDeserializeJs(json, "GhostCharacter")
        assertNotNull(result)
        assertEquals(42, getNumberField(result, "id"))
        assertEquals("Rick", getStringField(result, "name"))
        assertEquals("Alive", getStringField(result, "status"))
    }

    @Test
    fun testUnregisteredTypeHandling() {
        setupEnvironment()
        val result = ghostDeserializeJs("{}", "NonExistentModel")
        assertNull(result)
    }

    @Test
    fun testInvalidJsonHandling() {
        setupEnvironment()
        val result = ghostDeserializeJs("""{"id": 42, "name": }""", "GhostCharacter")
        assertNull(result)
    }
    
    @Test
    fun testEmptyStringJsonHandling() {
        setupEnvironment()
        val result = ghostDeserializeJs("", "GhostCharacter")
        assertNull(result, "Empty string should safely return null")
    }

    @Test
    fun testRegistryBuilderSafety() {
        setupEnvironment()
        try {
            GhostJsObjectRegistry.build("GhostCharacter", "NotACharacter")
        } catch (e: Exception) {
            // Expected ClassCastException
        }
    }

    @Test
    fun testNullAndOptionalFieldHandling() {
        setupEnvironment()
        val json = """{
            "info": {"count": 100, "pages": 5, "next": null, "prev": null},
            "results": []
        }"""
        val result = ghostDeserializeJs(json, "CharacterResponse")
        assertNotNull(result)
        
        val infoObj = getObjectField(result, "info")
        assertEquals(100, getNumberField(infoObj, "count"))
        assertTrue(isFieldNull(infoObj, "next"), "Null field should be strictly null in JS")
        assertTrue(isFieldNull(infoObj, "prev"), "Null field should be strictly null in JS")
    }

    @Test
    fun testMissingRequiredFieldHandling() {
        setupEnvironment()
        // Missing the required 'id' field (which has no default in BenchmarkModels)
        val json = """{"name": "Rick", "status": "Alive", "species": "Human", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}, "image": "url"}"""
        val result = ghostDeserializeJs(json, "GhostCharacter")
        
        // With strict validation, missing a non-nullable field without default SHOULD return null (caught error)
        assertNull(result, "Missing required field without default MUST return null for safety")
    }

    @Test
    fun testExtraUnknownFieldHandling() {
        setupEnvironment()
        // Contains "unknown_field" which should be ignored
        val json = """{"id": 42, "name": "Rick", "status": "Alive", "species": "Human", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}, "image": "url", "unknown_field": 999}"""
        val result = ghostDeserializeJs(json, "GhostCharacter")
        
        assertNotNull(result, "Extra fields should be safely ignored")
        assertEquals(42, getNumberField(result, "id"))
        assertTrue(isFieldUndefined(result, "unknown_field"), "Extra field should not be present in output JS object")
    }

    @Test
    fun testEmptyArrayHandling() {
        setupEnvironment()
        val json = """{"info": {"count": 0, "pages": 0}, "results": []}"""
        val result = ghostDeserializeJs(json, "CharacterResponse")
        
        assertNotNull(result)
        val resultsArray = getObjectField(result, "results")
        assertTrue(isArray(resultsArray), "Results should be a native JS Array")
        assertEquals(0, getArrayLength(resultsArray), "Empty array should have length 0")
    }

    @Test
    fun testTypeMismatchHandling() {
        setupEnvironment()
        // Passing a string where a number is expected ('id' is Int)
        val json = """{"id": "not_a_number", "name": "Rick"}"""
        val result = ghostDeserializeJs(json, "GhostCharacter")
        assertNull(result, "Type mismatches should be safely caught and return null")
    }

    @Test
    fun testNestedObjectHandling() {
        setupEnvironment()
        val json = """{
            "info": {"count": 1, "pages": 1, "next": "url"},
            "results": [{"id": 1, "name": "Rick", "status": "Alive", "species": "Human", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}, "image": "url"}]
        }"""
        val result = ghostDeserializeJs(json, "CharacterResponse")
        assertNotNull(result)
        
        val resultsArray = getObjectField(result, "results")
        assertEquals(1, getArrayLength(resultsArray))
        
        val firstResult = getArrayElement(resultsArray, 0)
        assertEquals(1, getNumberField(firstResult, "id"))
        assertEquals("Rick", getStringField(firstResult, "name"))
    }

    @Test
    fun testSpecialCharactersAndUnicodeHandling() {
        setupEnvironment()
        // Test JSON with escaped quotes, newlines, tabs, and Unicode (emojis)
        val json = """{"id": 99, "name": "Rick \"The Genius\" Sanchez \n\t \uD83D\uDE80", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}}"""
        val result = ghostDeserializeJs(json, "GhostCharacter")
        
        assertNotNull(result, "Should successfully parse special characters and unicode")
        assertEquals(99, getNumberField(result, "id"))
        
        // Let's assert the JS macro bindings preserved the exact decoded string structure
        val decodedName = getStringField(result, "name")
        val expectedName = "Rick \"The Genius\" Sanchez \n\t \uD83D\uDE80"
        assertEquals(expectedName, decodedName, "Unicode and escape sequences must remain fully intact")
    }
}
