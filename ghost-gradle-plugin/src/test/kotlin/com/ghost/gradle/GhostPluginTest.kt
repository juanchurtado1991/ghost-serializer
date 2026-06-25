package com.ghost.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.internal.project.DefaultProject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostPluginTest {

    @Test
    fun `plugin registers extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.findByName("ghost")
        assertNotNull(extension, "Ghost extension should be created")
    }

    @Test
    fun `plugin injects dependencies into jvm project`() {
        val project = ProjectBuilder.builder().build()

        // Simulate a JVM project
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(GhostPlugin::class.java)

        // Force the afterEvaluate block to run
        (project as DefaultProject).evaluate()

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
    fun `plugin auto-injects yaml when enabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.findByName("ghost") as GhostExtension
        extension.autoInjectYaml.set(true)

        (project as DefaultProject).evaluate()

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertTrue(
            implDependencies.any { it.name == "ghost-yaml" },
            "Should automatically inject ghost-yaml"
        )
    }

    @Test
    fun `plugin does not inject yaml when disabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.findByName("ghost") as GhostExtension
        extension.autoInjectYaml.set(false)

        (project as DefaultProject).evaluate()

        val implDependencies = project.configurations.getByName("implementation").dependencies
        assertTrue(
            implDependencies.none { it.name == "ghost-yaml" },
            "Should not inject ghost-yaml when disabled"
        )
    }
}
