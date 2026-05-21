package com.ghost.serialization.spring

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GhostSpringStarterTest {

    @Test
    fun autoConfigurationIsRegisteredForBoot3() {
        val imports = this::class.java.classLoader
            .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            ?.readText()
            ?: error("AutoConfiguration.imports missing from starter JAR resources")

        assertTrue(imports.contains("com.ghost.serialization.spring.GhostAutoConfiguration"))
        assertTrue(imports.contains("com.ghost.serialization.spring.GhostWebMvcAutoConfiguration"))
        assertTrue(imports.contains("com.ghost.serialization.spring.GhostWebFluxAutoConfiguration"))
    }
}
