package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.ApiProductConfig
import com.ghost.serialization.integration.model.ApiUserEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Multi-branch constructor codegen: for N default-valued properties (N ≤ 4), Ghost emits 2^N
 * explicit constructor branches instead of a `_result + .copy(...)` pattern.
 */
class GhostMultiBranchConstructorTest {

    // ApiProductConfig: N=2 default props (maxRetries=3, isEnabled=true) -> 4 branches

    @Test
    fun productConfig_allFieldsPresent_usesAllParsedValues() {
        val json = """{"id":1,"name":"Sync","maxRetries":5,"isEnabled":false}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(expected = 1, actual = result.id)
        assertEquals(expected = "Sync", actual = result.name)
        assertEquals(expected = 5, actual = result.maxRetries)
        assertEquals(expected = false, actual = result.isEnabled)
    }

    @Test
    fun productConfig_onlyRequiredFields_usesAllDefaults() {
        val json = """{"id":2,"name":"Batch"}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(expected = 2, actual = result.id)
        assertEquals(expected = "Batch", actual = result.name)
        assertEquals(expected = 3, actual = result.maxRetries)
        assertEquals(expected = true, actual = result.isEnabled)
    }

    @Test
    fun productConfig_onlyMaxRetriesPresent_isEnabledGetsDefault() {
        val json = """{"id":3,"name":"Worker","maxRetries":10}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(expected = 10, actual = result.maxRetries)
        assertEquals(expected = true, actual = result.isEnabled)
    }

    @Test
    fun productConfig_onlyIsEnabledPresent_maxRetriesGetsDefault() {
        val json = """{"id":4,"name":"Webhook","isEnabled":false}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(expected = 3, actual = result.maxRetries)
        assertEquals(expected = false, actual = result.isEnabled)
    }

    @Test
    fun productConfig_roundtrip_preservesAllValues() {
        val original =
            ApiProductConfig(id = 99, name = "RoundTrip", maxRetries = 7, isEnabled = false)
        val json = Ghost.serialize(original)
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(expected = original, actual = result)
    }

    // ApiUserEvent: N=3 default props (version=1, retryCount=0, isProcessed=false) -> 8 branches

    @Test
    fun userEvent_allFieldsPresent_usesAllParsedValues() {
        val json =
            """{"userId":10,"eventType":"purchase","version":3,"retryCount":2,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 10, actual = result.userId)
        assertEquals(expected = "purchase", actual = result.eventType)
        assertEquals(expected = 3, actual = result.version)
        assertEquals(expected = 2, actual = result.retryCount)
        assertEquals(expected = true, actual = result.isProcessed)
    }

    @Test
    fun userEvent_onlyRequiredFields_usesAllThreeDefaults() {
        val json = """{"userId":11,"eventType":"click"}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 1, actual = result.version)
        assertEquals(expected = 0, actual = result.retryCount)
        assertEquals(expected = false, actual = result.isProcessed)
    }

    @Test
    fun userEvent_versionOnly_otherTwoDefault() {
        val json = """{"userId":12,"eventType":"view","version":5}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 5, actual = result.version)
        assertEquals(expected = 0, actual = result.retryCount)
        assertEquals(expected = false, actual = result.isProcessed)
    }

    @Test
    fun userEvent_retryCountOnly_otherTwoDefault() {
        val json = """{"userId":13,"eventType":"retry","retryCount":4}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 1, actual = result.version)
        assertEquals(expected = 4, actual = result.retryCount)
        assertEquals(expected = false, actual = result.isProcessed)
    }

    @Test
    fun userEvent_isProcessedOnly_otherTwoDefault() {
        val json = """{"userId":14,"eventType":"ack","isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 1, actual = result.version)
        assertEquals(expected = 0, actual = result.retryCount)
        assertEquals(expected = true, actual = result.isProcessed)
    }

    @Test
    fun userEvent_versionAndRetryCount_isProcessedDefault() {
        val json = """{"userId":15,"eventType":"sync","version":2,"retryCount":3}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 2, actual = result.version)
        assertEquals(expected = 3, actual = result.retryCount)
        assertEquals(expected = false, actual = result.isProcessed)
    }

    @Test
    fun userEvent_versionAndIsProcessed_retryCountDefault() {
        val json = """{"userId":16,"eventType":"done","version":4,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 4, actual = result.version)
        assertEquals(expected = 0, actual = result.retryCount)
        assertEquals(expected = true, actual = result.isProcessed)
    }

    @Test
    fun userEvent_retryCountAndIsProcessed_versionDefault() {
        val json = """{"userId":17,"eventType":"fail","retryCount":9,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = 1, actual = result.version)
        assertEquals(expected = 9, actual = result.retryCount)
        assertEquals(expected = true, actual = result.isProcessed)
    }

    @Test
    fun userEvent_roundtrip_preservesAllValues() {
        val original = ApiUserEvent(
            userId = 42,
            eventType = "complete",
            version = 7,
            retryCount = 3,
            isProcessed = true
        )
        val json = Ghost.serialize(original)
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(expected = original, actual = result)
    }
}
