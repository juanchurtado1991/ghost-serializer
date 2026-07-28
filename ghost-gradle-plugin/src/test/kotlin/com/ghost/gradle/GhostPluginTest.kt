package com.ghost.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostPluginTest {

    private fun evaluated(project: Project) {
        (project as DefaultProject).evaluate()
    }

    @Test
    fun `plugin registers extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.findByName("ghost")
        assertNotNull(extension, "Ghost extension should be created")
    }

    @Test
    fun `plugin defaults ghost version to 1_3_0`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.getByType(GhostExtension::class.java)
        assertEquals("1.3.0", extension.version.get())
    }

    @Test
    fun `plugin injects dependencies into jvm project`() {
        val project = ProjectBuilder.builder().build()

        // Simulate a JVM project
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(GhostPlugin::class.java)

        // Force the afterEvaluate block to run
        evaluated(project)

        // Verify dependencies are added to "implementation"
        val implDependencies = project.configurations.getByName("implementation").dependencies

        assertTrue(
            implDependencies.any { it.name == "ghost-serialization" },
            "Should inject ghost-serialization"
        )
        assertTrue(
            implDependencies.any { it.name == "ghost-api" },
            "Should inject ghost-api"
        )
    }

    @Test
    fun `plugin injects ghost-ktor when ktor-client dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add("implementation", "io.ktor:ktor-client-core:3.5.1")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertTrue(
            implDependencies.any { it.name == "ghost-ktor" },
            "Should inject ghost-ktor when a ktor-client-* dependency is declared"
        )
    }

    @Test
    fun `plugin does not inject ghost-ktor when autoInjectKtor is disabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add("implementation", "io.ktor:ktor-client-core:3.5.1")
        project.pluginManager.apply(GhostPlugin::class.java)
        project.extensions.getByType(GhostExtension::class.java).autoInjectKtor.set(false)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertFalse(
            implDependencies.any { it.name == "ghost-ktor" },
            "Should not inject ghost-ktor when autoInjectKtor is false, even if ktor-client is present"
        )
    }

    @Test
    fun `plugin injects ghost-retrofit when retrofit dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add("implementation", "com.squareup.retrofit2:retrofit:2.9.0")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertTrue(
            implDependencies.any { it.name == "ghost-retrofit" },
            "Should inject ghost-retrofit when a retrofit dependency is declared"
        )
    }

    @Test
    fun `plugin does not inject ghost-retrofit when autoInjectRetrofit is disabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add("implementation", "com.squareup.retrofit2:retrofit:2.9.0")
        project.pluginManager.apply(GhostPlugin::class.java)
        project.extensions.getByType(GhostExtension::class.java).autoInjectRetrofit.set(false)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertFalse(
            implDependencies.any { it.name == "ghost-retrofit" },
            "Should not inject ghost-retrofit when autoInjectRetrofit is false, even if retrofit is present"
        )
    }

    @Test
    fun `plugin does not inject ghost-protobuf after proto merge into ghost-serialization`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add("implementation", "com.google.protobuf:protobuf-java:3.25.3")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertFalse(
            implDependencies.any { it.name == "ghost-protobuf" },
            "ghost-protobuf artifact was removed; proto support lives in ghost-serialization"
        )
    }

    @Test
    fun `plugin does not inject network adapters when no matching dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertFalse(implDependencies.any { it.name == "ghost-ktor" })
        assertFalse(implDependencies.any { it.name == "ghost-retrofit" })
    }

    @Test
    fun `plugin injects ghost-ktor into commonMainImplementation for kmp projects`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.dependencies.add("commonMainImplementation", "io.ktor:ktor-client-core:3.5.1")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val commonMainImplDependencies =
            project.configurations.getByName("commonMainImplementation").dependencies
        assertTrue(
            commonMainImplDependencies.any { it.name == "ghost-serialization" },
            "Should inject ghost-serialization into commonMainImplementation for KMP projects"
        )
        assertTrue(
            commonMainImplDependencies.any { it.name == "ghost-ktor" },
            "Should inject ghost-ktor into commonMainImplementation (not 'implementation') for KMP projects"
        )
    }

    @Test
    fun `plugin wires ksp for every declared kmp target plus the implicit metadata target`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project)

        val kspJvmDeps = project.configurations.getByName("kspJvm").dependencies
        assertTrue(
            kspJvmDeps.any { it.name == "ghost-compiler" },
            "Should add ghost-compiler to kspJvm for the declared jvm() target"
        )

        val kspCommonDeps = project.configurations.getByName("kspCommonMainMetadata").dependencies
        assertTrue(
            kspCommonDeps.any { it.name == "ghost-compiler" },
            "Should add ghost-compiler to kspCommonMainMetadata for the implicit 'metadata' target"
        )
    }
}
