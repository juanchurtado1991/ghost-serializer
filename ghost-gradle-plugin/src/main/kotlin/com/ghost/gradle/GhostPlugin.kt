package com.ghost.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Gradle plugin (id `com.ghostserializer.ghost`) that wires Ghost Serialization into Android, JVM,
 * and KMP projects: adds `ghost-compiler` to KSP and adds the runtime/API dependencies. Configure
 * via the [GhostExtension] `ghost` block. Network-adapter auto-injection (`ghost-ktor`/
 * `ghost-retrofit`) is a separate concern — see [GhostNetworkAutoInjector].
 *
 * KSP wiring is reactive, via `PluginContainer.withId` listeners, so it works regardless of
 * whether the KSP/KMP/Android plugins are applied before or after this plugin.
 */
class GhostPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = createExtension(project = project)
        wireKspAutoConfiguration(
            project = project,
            ghostVersion = extension.version
        )
        GhostNetworkAutoInjector.wireNetworkAutoInjection(
            project = project,
            extension = extension
        )
    }

    private fun addCoreDependencies(project: Project, version: Provider<String>, configuration: String) {
        val runtimeDep = version.map { "$GROUP_ID:$ARTIFACT_RUNTIME:$it" }
        val apiDep = version.map { "$GROUP_ID:$ARTIFACT_API:$it" }
        project.dependencies.add(configuration, runtimeDep)
        project.dependencies.add(configuration, apiDep)
    }

    private fun configureKspForKmp(project: Project, compilerDep: Provider<String>) {
        val kotlinExtension = project
            .extensions
            .findByType(KotlinMultiplatformExtension::class.java)

        kotlinExtension?.targets?.configureEach {
            if (name == TARGET_METADATA) {
                project.dependencies.add(CONFIG_KSP_COMMON, compilerDep)
            } else {
                val capitalizedTarget = name.replaceFirstChar { it.uppercase() }
                project.dependencies.add(
                    "$PREFIX_KSP$capitalizedTarget",
                    compilerDep
                )
            }
        }
    }

    private fun createExtension(project: Project): GhostExtension {
        return project.extensions.create(EXTENSION_NAME, GhostExtension::class.java).apply {
            autoInjectKtor.convention(true)
            autoInjectRetrofit.convention(true)
            version.convention(DEFAULT_VERSION)
        }
    }

    private fun isAndroidOrJvmProject(project: Project): Boolean =
        project.plugins.hasPlugin(PLUGIN_ANDROID_APP) ||
                project.plugins.hasPlugin(PLUGIN_ANDROID_LIB) ||
                project.plugins.hasPlugin(PLUGIN_JVM)

    private fun tryAddKspCompilerDependency(
        project: Project,
        ghostVersion: Provider<String>
    ): Boolean {
        val compilerDep = ghostVersion.map { "$GROUP_ID:$ARTIFACT_COMPILER:$it" }
        return when {
            project.plugins.hasPlugin(PLUGIN_KMP) -> {
                configureKspForKmp(
                    project = project,
                    compilerDep = compilerDep
                )
                true
            }

            isAndroidOrJvmProject(project = project) -> {
                project.dependencies.add(
                    PREFIX_KSP,
                    compilerDep
                )
                true
            }

            else -> false
        }
    }

    private fun wireAndroidOrJvmCoreSetup(
        project: Project,
        version: Provider<String>,
        onCoreApplied: () -> Unit
    ) {
        var coreApplied = false

        listOf(PLUGIN_ANDROID_APP, PLUGIN_ANDROID_LIB, PLUGIN_JVM).forEach { pluginId ->
            project.plugins.withId(pluginId) {
                if (!coreApplied) {
                    addCoreDependencies(
                        project = project,
                        version = version,
                        configuration = CONFIG_IMPL
                    )
                    coreApplied = true
                }
                onCoreApplied()
            }
        }
    }

    private fun wireKspAutoConfiguration(
        project: Project,
        ghostVersion: Provider<String>
    ) {
        var kspSetupDone = false
        fun configureKspDependenciesOnce() {
            if (kspSetupDone) return
            if (!project.plugins.hasPlugin(PLUGIN_KSP)) return
            kspSetupDone = tryAddKspCompilerDependency(
                project = project,
                ghostVersion = ghostVersion
            )
        }

        project.plugins.withId(PLUGIN_KSP) {
            configureKspDependenciesOnce()
        }

        project.plugins.withId(PLUGIN_KMP) {
            addCoreDependencies(
                project = project,
                version = ghostVersion,
                configuration = CONFIG_COMMON_MAIN_IMPL
            )
            configureKspDependenciesOnce()
        }

        wireAndroidOrJvmCoreSetup(
            project = project,
            version = ghostVersion,
            onCoreApplied = ::configureKspDependenciesOnce
        )
    }

    companion object {
        private const val EXTENSION_NAME = "ghost"

        private const val PLUGIN_KSP = "com.google.devtools.ksp"
        private const val PLUGIN_KMP = "org.jetbrains.kotlin.multiplatform"
        private const val PLUGIN_ANDROID_APP = "com.android.application"
        private const val PLUGIN_ANDROID_LIB = "com.android.library"
        private const val PLUGIN_JVM = "org.jetbrains.kotlin.jvm"

        private const val GROUP_ID = "com.ghostserializer"
        private const val ARTIFACT_COMPILER = "ghost-compiler"
        private const val ARTIFACT_RUNTIME = "ghost-serialization"
        private const val ARTIFACT_API = "ghost-api"

        private const val CONFIG_COMMON_MAIN_IMPL = "commonMainImplementation"
        private const val CONFIG_IMPL = "implementation"
        private const val CONFIG_KSP_COMMON = "kspCommonMainMetadata"
        private const val PREFIX_KSP = "ksp"

        private const val TARGET_METADATA = "metadata"
    }
}
