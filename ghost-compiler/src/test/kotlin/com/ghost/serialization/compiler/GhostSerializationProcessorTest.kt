package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.internal.GhostProcessorConstants
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Smoke tests for the KSP processor entry point.
 * Full compile-time KSP coverage lives in :ghost-integration-test (real Gradle + KSP pipeline).
 */
class GhostSerializationProcessorTest {

    @Test
    fun providerIsDiscoverable() {
        val provider = GhostSerializationProvider()
        assertNotNull(actual = provider)
        assertEquals(expected = "GhostSerializationProvider", actual = provider::class.simpleName)
    }

    @Test
    fun registryPrefixIsStable() {
        assertEquals(expected = "GhostModuleRegistry", actual = GhostProcessorConstants.STR_REGISTRY_PREFIX)
    }
}
