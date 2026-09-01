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
        assertEquals(1, result.id)
        assertEquals("Sync", result.name)
        assertEquals(5, result.maxRetries)
        assertEquals(false, result.isEnabled)
    }

    @Test
    fun productConfig_onlyRequiredFields_usesAllDefaults() {
        val json = """{"id":2,"name":"Batch"}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(2, result.id)
        assertEquals("Batch", result.name)
        assertEquals(3, result.maxRetries)
        assertEquals(true, result.isEnabled)
    }

    @Test
    fun productConfig_onlyMaxRetriesPresent_isEnabledGetsDefault() {
        val json = """{"id":3,"name":"Worker","maxRetries":10}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(10, result.maxRetries)
        assertEquals(true, result.isEnabled)
    }

    @Test
    fun productConfig_onlyIsEnabledPresent_maxRetriesGetsDefault() {
        val json = """{"id":4,"name":"Webhook","isEnabled":false}"""
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(3, result.maxRetries)
        assertEquals(false, result.isEnabled)
    }

    @Test
    fun productConfig_roundtrip_preservesAllValues() {
        val original =
            ApiProductConfig(id = 99, name = "RoundTrip", maxRetries = 7, isEnabled = false)
        val json = Ghost.serialize(original)
        val result = Ghost.deserialize<ApiProductConfig>(json)
        assertEquals(original, result)
    }

    // ApiUserEvent: N=3 default props (version=1, retryCount=0, isProcessed=false) -> 8 branches

    @Test
    fun userEvent_allFieldsPresent_usesAllParsedValues() {
        val json =
            """{"userId":10,"eventType":"purchase","version":3,"retryCount":2,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(10, result.userId)
        assertEquals("purchase", result.eventType)
        assertEquals(3, result.version)
        assertEquals(2, result.retryCount)
        assertEquals(true, result.isProcessed)
    }

    @Test
    fun userEvent_onlyRequiredFields_usesAllThreeDefaults() {
        val json = """{"userId":11,"eventType":"click"}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(1, result.version)
        assertEquals(0, result.retryCount)
        assertEquals(false, result.isProcessed)
    }

    @Test
    fun userEvent_versionOnly_otherTwoDefault() {
        val json = """{"userId":12,"eventType":"view","version":5}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(5, result.version)
        assertEquals(0, result.retryCount)
        assertEquals(false, result.isProcessed)
    }

    @Test
    fun userEvent_retryCountOnly_otherTwoDefault() {
        val json = """{"userId":13,"eventType":"retry","retryCount":4}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(1, result.version)
        assertEquals(4, result.retryCount)
        assertEquals(false, result.isProcessed)
    }

    @Test
    fun userEvent_isProcessedOnly_otherTwoDefault() {
        val json = """{"userId":14,"eventType":"ack","isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(1, result.version)
        assertEquals(0, result.retryCount)
        assertEquals(true, result.isProcessed)
    }

    @Test
    fun userEvent_versionAndRetryCount_isProcessedDefault() {
        val json = """{"userId":15,"eventType":"sync","version":2,"retryCount":3}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(2, result.version)
        assertEquals(3, result.retryCount)
        assertEquals(false, result.isProcessed)
    }

    @Test
    fun userEvent_versionAndIsProcessed_retryCountDefault() {
        val json = """{"userId":16,"eventType":"done","version":4,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(4, result.version)
        assertEquals(0, result.retryCount)
        assertEquals(true, result.isProcessed)
    }

    @Test
    fun userEvent_retryCountAndIsProcessed_versionDefault() {
        val json = """{"userId":17,"eventType":"fail","retryCount":9,"isProcessed":true}"""
        val result = Ghost.deserialize<ApiUserEvent>(json)
        assertEquals(1, result.version)
        assertEquals(9, result.retryCount)
        assertEquals(true, result.isProcessed)
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
        assertEquals(original, result)
    }
}
