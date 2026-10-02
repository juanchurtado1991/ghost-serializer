package com.ghost.gradle

import com.ghost.gradle.GhostPluginTestConstants as T
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostPluginTest {

    private fun evaluated(
        project: Project
    ) {
        (project as DefaultProject).evaluate()
    }

    @Test
    fun `plugin registers extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.findByName("ghost")
        assertNotNull(
            actual = extension,
            message = "Ghost extension should be created"
        )
    }

    @Test
    fun `plugin defaults ghost version from publish catalog`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.getByType(GhostExtension::class.java)
        assertEquals(
            expected = DEFAULT_VERSION,
            actual = extension.version.get()
        )
    }

    @Test
    fun `plugin injects dependencies into jvm project`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.pluginManager.apply(GhostPlugin::class.java)

        // Dependency injection happens inside afterEvaluate, so it must be forced to run.
        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies

        assertTrue(
            actual = implDependencies.any { it.name == T.ARTIFACT_SERIALIZATION },
            message = "Should inject ghost-serialization"
        )
        assertTrue(
            actual = implDependencies.any { it.name == T.ARTIFACT_API },
            message = "Should inject ghost-api"
        )
    }

    @Test
    fun `plugin injects ghost-ktor when ktor-client dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.dependencies.add(T.CONFIG_IMPLEMENTATION, T.KTOR_CLIENT_COORDINATE)
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertTrue(
            actual = implDependencies.any { it.name == T.ARTIFACT_KTOR },
            message = "Should inject ghost-ktor when a ktor-client-* dependency is declared"
        )
    }

    @Test
    fun `plugin does not inject ghost-ktor when autoInjectKtor is disabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.dependencies.add(T.CONFIG_IMPLEMENTATION, T.KTOR_CLIENT_COORDINATE)
        project.pluginManager.apply(GhostPlugin::class.java)
        project.extensions.getByType(GhostExtension::class.java).autoInjectKtor.set(false)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertFalse(
            actual = implDependencies.any { it.name == T.ARTIFACT_KTOR },
            message = "Should not inject ghost-ktor when autoInjectKtor is false, even if ktor-client is present"
        )
    }

    @Test
    fun `plugin injects ghost-retrofit when retrofit dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.dependencies.add(T.CONFIG_IMPLEMENTATION, T.RETROFIT_COORDINATE)
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertTrue(
            actual = implDependencies.any { it.name == T.ARTIFACT_RETROFIT },
            message = "Should inject ghost-retrofit when a retrofit dependency is declared"
        )
    }

    @Test
    fun `plugin does not inject ghost-retrofit when autoInjectRetrofit is disabled`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.dependencies.add(T.CONFIG_IMPLEMENTATION, T.RETROFIT_COORDINATE)
        project.pluginManager.apply(GhostPlugin::class.java)
        project.extensions.getByType(GhostExtension::class.java).autoInjectRetrofit.set(false)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertFalse(
            actual = implDependencies.any { it.name == T.ARTIFACT_RETROFIT },
            message = "Should not inject ghost-retrofit when autoInjectRetrofit is false, even if retrofit is present"
        )
    }

    @Test
    fun `plugin does not inject ghost-protobuf after proto merge into ghost-serialization`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.dependencies.add(T.CONFIG_IMPLEMENTATION, "com.google.protobuf:protobuf-java:3.25.3")
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertFalse(
            actual = implDependencies.any { it.name == "ghost-protobuf" },
            message = "ghost-protobuf artifact was removed; proto support lives in ghost-serialization"
        )
    }

    @Test
    fun `plugin does not inject network adapters when no matching dependency is present`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val implDependencies = project.configurations.getByName(T.CONFIG_IMPLEMENTATION).dependencies
        assertFalse(actual = implDependencies.any { it.name == T.ARTIFACT_KTOR })
        assertFalse(actual = implDependencies.any { it.name == T.ARTIFACT_RETROFIT })
    }

    @Test
    fun `plugin injects ghost-ktor into commonMainImplementation for kmp projects`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_MULTIPLATFORM)
        project.dependencies.add(T.CONFIG_COMMON_MAIN_IMPLEMENTATION, T.KTOR_CLIENT_COORDINATE)
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val commonMainImplDependencies =
            project.configurations.getByName(T.CONFIG_COMMON_MAIN_IMPLEMENTATION).dependencies
        assertTrue(
            actual = commonMainImplDependencies.any { it.name == T.ARTIFACT_SERIALIZATION },
            message = "Should inject ghost-serialization into commonMainImplementation for KMP projects"
        )
        assertTrue(
            actual = commonMainImplDependencies.any { it.name == T.ARTIFACT_KTOR },
            message = "Should inject ghost-ktor into commonMainImplementation (not 'implementation') for KMP projects"
        )
    }

    @Test
    fun `plugin wires ksp for every declared kmp target plus the implicit metadata target`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(T.PLUGIN_KOTLIN_MULTIPLATFORM)
        project.pluginManager.apply(T.PLUGIN_KSP)
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
        project.pluginManager.apply(GhostPlugin::class.java)

        evaluated(project = project)

        val kspJvmDeps = project.configurations.getByName("kspJvm").dependencies
        assertTrue(
            actual = kspJvmDeps.any { it.name == T.ARTIFACT_COMPILER },
            message = "Should add ghost-compiler to kspJvm for the declared jvm() target"
        )

        val kspCommonDeps = project.configurations.getByName("kspCommonMainMetadata").dependencies
        assertTrue(
            actual = kspCommonDeps.any { it.name == T.ARTIFACT_COMPILER },
            message = "Should add ghost-compiler to kspCommonMainMetadata for the implicit 'metadata' target"
        )
    }

    @Test
    fun `plugin defaults autoRegistration to true`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(GhostPlugin::class.java)

        val extension = project.extensions.getByType(GhostExtension::class.java)
        assertTrue(actual = extension.autoRegistration.get())
    }

    @Test
    fun `plugin applies compiler plugin support only to kmp projects`() {
        val jvmProject = ProjectBuilder.builder().build()
        jvmProject.pluginManager.apply(T.PLUGIN_KOTLIN_JVM)
        jvmProject.pluginManager.apply(GhostPlugin::class.java)

        val kmpProject = ProjectBuilder.builder().build()
        kmpProject.pluginManager.apply(T.PLUGIN_KOTLIN_MULTIPLATFORM)
        kmpProject.pluginManager.apply(GhostPlugin::class.java)

        assertFalse(actual = jvmProject.plugins.hasPlugin(GhostCompilerPluginSupport::class.java))
        assertTrue(actual = kmpProject.plugins.hasPlugin(GhostCompilerPluginSupport::class.java))
    }

    @Test
    fun `compiler plugin is added to wasm compilations but not jvm compilations`() {
        val project = kmpProjectWithJvmAndWasm()

        evaluated(project = project)

        val wasmPlugins = project.configurations.getByName(T.CONFIG_PLUGIN_CLASSPATH_WASM_MAIN).dependencies
        val jvmPlugins = project.configurations.getByName(T.CONFIG_PLUGIN_CLASSPATH_JVM_MAIN).dependencies
        assertTrue(
            actual = wasmPlugins.any { it.name == T.ARTIFACT_COMPILER_PLUGIN },
            message = "Should add ghost-compiler-plugin to the wasmJs main compilation"
        )
        assertFalse(
            actual = jvmPlugins.any { it.name == T.ARTIFACT_COMPILER_PLUGIN },
            message = "JVM compilations keep ServiceLoader discovery and must not get the compiler plugin"
        )
    }

    @OptIn(ExperimentalWasmDsl::class)
    @Test
    fun `compiler plugin option follows autoRegistration`() {
        val project = kmpProjectWithJvmAndWasm()
        project.extensions.getByType(GhostExtension::class.java).autoRegistration.set(false)

        evaluated(project = project)

        val wasmMain = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            .wasmJs()
            .compilations
            .getByName(T.COMPILATION_MAIN)
        val options = GhostCompilerPluginSupport().applyToCompilation(kotlinCompilation = wasmMain).get()
        assertEquals(
            expected = false.toString(),
            actual = options.single { it.key == T.OPTION_ENABLED }.value
        )
    }

    @OptIn(ExperimentalWasmDsl::class)
    private fun kmpProjectWithJvmAndWasm(): Project {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(T.PLUGIN_KOTLIN_MULTIPLATFORM)
        project.pluginManager.apply(GhostPlugin::class.java)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.jvm()
        kotlin.wasmJs {
            nodejs()
        }
        return project
    }
}
