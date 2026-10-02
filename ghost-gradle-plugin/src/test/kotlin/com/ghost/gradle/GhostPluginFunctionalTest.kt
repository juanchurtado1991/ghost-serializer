package com.ghost.gradle

import com.ghost.gradle.GhostPluginTestConstants as T
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class GhostPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private val buildFile by lazy { testProjectDir.resolve(relative = "build.gradle.kts") }
    private val settingsFile by lazy { testProjectDir.resolve(relative = "settings.gradle.kts") }

    private val kotlinVersion: String
        get() = System.getProperty("kotlinVersion") ?: "2.4.0"

    private val kspVersion: String
        get() = System.getProperty("kspVersion") ?: "2.3.10"

    private val ghostVersion: String
        get() = System.getProperty("ghostVersion") ?: "1.3.1"

    @Test
    fun `plugin supports configuration cache`() {
        settingsFile.writeText(text = "rootProject.name = \"cache-test\"")
        buildFile.writeText(
            text = """
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

        val result1 = runner.build()
        assertTrue(
            actual = result1.output.contains(other = "Configuration cache entry stored."),
            message = "Should store configuration cache"
        )

        val result2 = runner.build()
        assertTrue(
            actual = result2.output.contains(other = "Reusing configuration cache."),
            message = "Should reuse configuration cache"
        )
    }

    @Test
    fun `plugin handles incremental builds correctly`() {
        settingsFile.writeText(text = "rootProject.name = \"incremental-test\"")

        val srcDir = testProjectDir.resolve(relative = T.SOURCE_DIR)
        srcDir.mkdirs()
        val modelFile = srcDir.resolve(relative = T.MODEL_FILE)
        modelFile.writeText(
            text = """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String)
        """.trimIndent()
        )

        buildFile.writeText(
            text = """
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
            .withArguments(T.TASK_KSP_KOTLIN)
            .withPluginClasspath()
            .forwardOutput()

        runner.build()

        modelFile.writeText(
            text = """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String, val age: Int)
        """.trimIndent()
        )

        val result = runner.build()
        assertTrue(
            actual = result.output.contains(other = "SUCCESS"),
            message = "Incremental build should succeed"
        )
    }

    @Test
    fun `plugin works when applied before KSP`() {
        settingsFile.writeText(text = "rootProject.name = \"order-test\"")

        val srcDir = testProjectDir.resolve(relative = T.SOURCE_DIR)
        srcDir.mkdirs()
        val modelFile = srcDir.resolve(relative = T.MODEL_FILE)
        modelFile.writeText(
            text = """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String)
        """.trimIndent()
        )

        buildFile.writeText(
            text = """
            plugins {
                id("com.ghostserializer.ghost")
                kotlin("jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
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
            .withArguments(T.TASK_KSP_KOTLIN)
            .withPluginClasspath()
            .forwardOutput()

        val result = runner.build()
        assertTrue(
            actual = result.output.contains(other = "SUCCESS"),
            message = "Build with plugin applied before KSP should succeed"
        )
    }

    @Test
    fun `ksp textChannel option generates native string deserialize overload`() {
        settingsFile.writeText(text = "rootProject.name = \"text-channel-test\"")

        val srcDir = testProjectDir.resolve(relative = T.SOURCE_DIR)
        srcDir.mkdirs()
        srcDir.resolve(relative = T.MODEL_FILE).writeText(
            text = """
            package com.example
            import com.ghost.serialization.annotations.GhostSerialization
            @GhostSerialization
            data class Model(val name: String)
        """.trimIndent()
        )

        buildFile.writeText(
            text = """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.ghostserializer.ghost")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            ksp {
                arg("ghost.textChannel", "true")
            }

            ghost {
                version.set("$ghostVersion")
                autoInjectKtor.set(false)
                autoInjectRetrofit.set(false)
            }
        """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(T.TASK_KSP_KOTLIN)
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val generated = testProjectDir.walk()
            .filter { it.name == "ModelSerializer.kt" }
            .map { it.readText() }
            .firstOrNull()

        assertTrue(
            actual = generated != null,
            message = "Expected generated ModelSerializer.kt"
        )
        assertTrue(
            actual = "override fun deserialize(reader: GhostJsonStringReader)" in generated!!,
            message = "Expected native string deserialize when ghost.textChannel=true:\n$generated"
        )
    }
}
