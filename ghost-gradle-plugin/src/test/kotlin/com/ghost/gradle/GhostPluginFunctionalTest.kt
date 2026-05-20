package com.ghost.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class GhostPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private val buildFile by lazy { testProjectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { testProjectDir.resolve("settings.gradle.kts") }

    private val kotlinVersion: String
        get() = System.getProperty("kotlinVersion") ?: "1.9.24"

    private val kspVersion: String
        get() = System.getProperty("kspVersion") ?: "1.9.24-1.0.20"

    private val ghostVersion: String
        get() = System.getProperty("ghostVersion") ?: "1.1.16"

    @Test
    fun `plugin supports configuration cache`() {
        settingsFile.writeText("rootProject.name = \"cache-test\"")
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.ghostserializer.ghost")
            }
            
            repositories {
                mavenLocal()
                mavenCentral()
            }
            
            ghost {
                version.set("$ghostVersion")
                autoInjectKtor.set(false)
                autoInjectRetrofit.set(false)
            }
        """.trimIndent()
        )

        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("help", "--configuration-cache")
            .withPluginClasspath()
            .forwardOutput()

        // First run - calculates cache
        val result1 = runner.build()
        assertTrue(
            result1.output.contains("Configuration cache entry stored."),
            "Should store configuration cache"
        )

        // Second run - reuses cache
        val result2 = runner.build()
        assertTrue(
            result2.output.contains("Reusing configuration cache."),
            "Should reuse configuration cache"
        )
    }

    @Test
    fun `plugin handles incremental builds correctly`() {
        settingsFile.writeText("rootProject.name = \"incremental-test\"")

        val srcDir = testProjectDir.resolve("src/main/kotlin/com/example")
        srcDir.mkdirs()
        val modelFile = srcDir.resolve("Model.kt")
        modelFile.writeText(
            """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String)
        """.trimIndent()
        )

        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.ghostserializer.ghost")
            }
            
            repositories {
                mavenLocal()
                mavenCentral()
            }
            
            ghost {
                version.set("$ghostVersion")
                autoInjectKtor.set(false)
                autoInjectRetrofit.set(false)
            }
        """.trimIndent()
        )

        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("kspKotlin")
            .withPluginClasspath()
            .forwardOutput()

        // First build
        runner.build()

        // Change model
        modelFile.writeText(
            """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String, val age: Int)
        """.trimIndent()
        )

        // Second build - should be successful and incremental
        val result = runner.build()
        assertTrue(result.output.contains("SUCCESS"), "Incremental build should succeed")
    }
}
