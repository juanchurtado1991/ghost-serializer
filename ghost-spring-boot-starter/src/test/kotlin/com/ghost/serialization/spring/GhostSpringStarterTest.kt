package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.checkPayloadSize
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostSpringStarterTest {

    @Test
    fun autoConfigurationIsRegisteredForBoot3() {
        val imports = this::class.java.classLoader
            .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            ?.readText()
            ?: error("AutoConfiguration.imports missing from starter JAR resources")

        assertTrue(imports.contains("com.ghost.serialization.spring.GhostAutoConfiguration"))
    }

    @Test
    fun payloadGuard_matchesSerializationModuleLimit() {
        val error = assertFailsWith<GhostJsonException> {
            checkPayloadSize(GhostHeuristics.maxPayloadBytes + 1)
        }
        assertTrue(error.message!!.contains("maximum allowed size"))
    }

    @Test
    fun ghostProperties_applyMaxPayloadBytesOnStartup() {
        val custom = 32 * 1024 * 1024
        try {
            GhostPayloadConfiguration(GhostProperties().apply { maxPayloadBytes = custom })
                .applyMaxPayloadBytes()
            assertEquals(custom, Ghost.maxPayloadBytes)
        } finally {
            Ghost.resetMaxPayloadBytes()
        }
    }
}
