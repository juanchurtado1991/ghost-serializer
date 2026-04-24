package com.ghost.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class GhostPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = createExtension(project)
        applyKspPlugin(project)

        project.afterEvaluate {
            val ghostVersion = extension.version.get()
            injectCoreDependencies(project, ghostVersion)
            injectKspCompiler(project, ghostVersion)
            injectNetworkAdapters(project, extension, ghostVersion)
        }
    }

    private fun createExtension(project: Project): GhostExtension {
        return project.extensions.create(EXTENSION_NAME, GhostExtension::class.java).apply {
            autoInjectKtor.convention(true)
            autoInjectRetrofit.convention(true)
            version.convention(DEFAULT_VERSION)
        }
    }

    private fun applyKspPlugin(project: Project) {
        if (!project.pluginManager.hasPlugin(PLUGIN_KSP)) {
            try {
                project.pluginManager.apply(PLUGIN_KSP)
            } catch (_: Exception) {
                project.logger.warn("GhostPlugin: Could not automatically " +
                        "apply KSP. Ensure '$PLUGIN_KSP' is in your plugin classpath.")
            }
        }
    }

    private fun injectCoreDependencies(project: Project, version: String) {
        val runtimeDependency = "$GROUP_ID:$ARTIFACT_RUNTIME:$version"
        val apiDependency = "$GROUP_ID:$ARTIFACT_API:$version"

        if (isMultiplatform(project)) {
            project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, runtimeDependency)
            project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, apiDependency)
        } else if (isAndroidOrJvm(project)) {
            project.dependencies.add(CONFIG_IMPL, runtimeDependency)
            project.dependencies.add(CONFIG_IMPL, apiDependency)
        }
    }

    private fun injectKspCompiler(project: Project, version: String) {
        if (!project.pluginManager.hasPlugin(PLUGIN_KSP)) {
            return
        }

        val compilerDependency = "$GROUP_ID:$ARTIFACT_COMPILER:$version"

        if (isMultiplatform(project)) {
            val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            kotlinExtension?.targets?.forEach { target ->
                if (target.name == TARGET_METADATA) {
                    project.dependencies.add(CONFIG_KSP_COMMON, compilerDependency)
                } else {
                    val capitalizedTarget = target.name.replaceFirstChar { it.uppercase() }
                    project.dependencies.add("$PREFIX_KSP$capitalizedTarget", compilerDependency)
                }
            }
        } else if (isAndroidOrJvm(project)) {
            project.dependencies.add(PREFIX_KSP, compilerDependency)
        }
    }

    private fun injectNetworkAdapters(project: Project, extension: GhostExtension, version: String) {
        if (extension.autoInjectKtor.get() && hasKtorDependency(project)) {
            injectDependency(project, "$GROUP_ID:$ARTIFACT_KTOR:$version")
        }

        if (extension.autoInjectRetrofit.get() && hasRetrofitDependency(project)) {
            injectDependency(project, "$GROUP_ID:$ARTIFACT_RETROFIT:$version", forceImplementation = true)
        }
    }

    private fun injectDependency(project: Project, dependency: String, forceImplementation: Boolean = false) {
        if (isMultiplatform(project) && !forceImplementation) {
            project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, dependency)
        } else {
            project.dependencies.add(CONFIG_IMPL, dependency)
        }
    }

    private fun hasKtorDependency(project: Project): Boolean {
        return project.configurations.any { config ->
            config.dependencies.any { it.group == GROUP_KTOR && it.name.startsWith(PREFIX_KTOR_CLIENT) }
        }
    }

    private fun hasRetrofitDependency(project: Project): Boolean {
        return project.configurations.any { config ->
            config.dependencies.any { it.group == GROUP_RETROFIT && it.name == NAME_RETROFIT }
        }
    }

    private fun isMultiplatform(project: Project): Boolean {
        return project.pluginManager.hasPlugin(PLUGIN_KMP)
    }

    private fun isAndroidOrJvm(project: Project): Boolean {
        return project.pluginManager.hasPlugin(PLUGIN_ANDROID_APP) ||
                project.pluginManager.hasPlugin(PLUGIN_ANDROID_LIB) ||
                project.pluginManager.hasPlugin(PLUGIN_JVM)
    }

    companion object {
        private const val EXTENSION_NAME = "ghost"
        private const val DEFAULT_VERSION = "1.1.8"

        private const val PLUGIN_KSP = "com.google.devtools.ksp"
        private const val PLUGIN_KMP = "org.jetbrains.kotlin.multiplatform"
        private const val PLUGIN_ANDROID_APP = "com.android.application"
        private const val PLUGIN_ANDROID_LIB = "com.android.library"
        private const val PLUGIN_JVM = "org.jetbrains.kotlin.jvm"

        private const val GROUP_ID = "com.ghostserializer"
        private const val ARTIFACT_COMPILER = "ghost-compiler"
        private const val ARTIFACT_RUNTIME = "ghost-serialization"
        private const val ARTIFACT_API = "ghost-api"
        private const val ARTIFACT_KTOR = "ghost-ktor"
        private const val ARTIFACT_RETROFIT = "ghost-retrofit"

        private const val CONFIG_COMMON_MAIN_IMPL = "commonMainImplementation"
        private const val CONFIG_IMPL = "implementation"
        private const val CONFIG_KSP_COMMON = "kspCommonMainMetadata"
        private const val PREFIX_KSP = "ksp"

        private const val TARGET_METADATA = "metadata"

        private const val GROUP_KTOR = "io.ktor"
        private const val PREFIX_KTOR_CLIENT = "ktor-client"
        private const val GROUP_RETROFIT = "com.squareup.retrofit2"
        private const val NAME_RETROFIT = "retrofit"
    }
}
