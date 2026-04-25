package com.ghost.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class GhostPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = createExtension(project)
        val ghostVersion = extension.version

        // 1. Core & KSP (Reactive)
        project.plugins.withId(PLUGIN_KSP) { setupKsp(project, ghostVersion) }
        project.plugins.withId(PLUGIN_KMP) { setupKmp(project, extension, ghostVersion) }
        
        var coreApplied = false
        listOf(PLUGIN_ANDROID_APP, PLUGIN_ANDROID_LIB, PLUGIN_JVM).forEach { pluginId ->
            project.plugins.withId(pluginId) {
                if (!coreApplied) {
                    setupAndroidOrJvmCore(project, ghostVersion)
                    coreApplied = true
                }
            }
        }

        // 2. Network Adapters (Safe afterEvaluate)
        // We use afterEvaluate to perform the check ONCE after all dependencies are declared,
        // avoiding the circularity of providers and the variant resolution issues of configurations.all.
        project.afterEvaluate {
            val version = ghostVersion.get()
            if (extension.autoInjectKtor.get() && hasKtorDependency(project)) {
                injectNetworkDependency(project, "$GROUP_ID:$ARTIFACT_KTOR:$version")
            }
            if (extension.autoInjectRetrofit.get() && hasRetrofitDependency(project)) {
                injectNetworkDependency(project, "$GROUP_ID:$ARTIFACT_RETROFIT:$version")
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

    private fun setupKmp(project: Project, extension: GhostExtension, version: Provider<String>) {
        val runtimeDep = version.map { "$GROUP_ID:$ARTIFACT_RUNTIME:$it" }
        val apiDep = version.map { "$GROUP_ID:$ARTIFACT_API:$it" }
        
        project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, runtimeDep)
        project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, apiDep)
    }

    private fun setupAndroidOrJvmCore(project: Project, version: Provider<String>) {
        val runtimeDep = version.map { "$GROUP_ID:$ARTIFACT_RUNTIME:$it" }
        val apiDep = version.map { "$GROUP_ID:$ARTIFACT_API:$it" }
        project.dependencies.add(CONFIG_IMPL, runtimeDep)
        project.dependencies.add(CONFIG_IMPL, apiDep)
    }

    private fun setupKsp(project: Project, version: Provider<String>) {
        val compilerDep = version.map { "$GROUP_ID:$ARTIFACT_COMPILER:$it" }
        
        if (isMultiplatform(project)) {
            val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            kotlinExtension?.targets?.configureEach {
                if (name == TARGET_METADATA) {
                    project.dependencies.add(CONFIG_KSP_COMMON, compilerDep)
                } else {
                    val capitalizedTarget = name.replaceFirstChar { it.uppercase() }
                    project.dependencies.add("$PREFIX_KSP$capitalizedTarget", compilerDep)
                }
            }
        } else {
            project.dependencies.add(PREFIX_KSP, compilerDep)
        }
    }



    private fun isMultiplatform(project: Project): Boolean {
        return project.pluginManager.hasPlugin(PLUGIN_KMP)
    }

    private fun hasKtorDependency(project: Project): Boolean {
        return listOf(CONFIG_IMPL, "api", CONFIG_COMMON_MAIN_IMPL).any { name ->
            val config = project.configurations.findByName(name)
            config?.dependencies?.any { it.group == GROUP_KTOR && it.name.startsWith(PREFIX_KTOR_CLIENT) } ?: false
        }
    }

    private fun hasRetrofitDependency(project: Project): Boolean {
        return listOf(CONFIG_IMPL, "api", CONFIG_COMMON_MAIN_IMPL).any { name ->
            val config = project.configurations.findByName(name)
            config?.dependencies?.any { it.group == GROUP_RETROFIT && it.name == NAME_RETROFIT } ?: false
        }
    }



    private fun injectNetworkDependency(project: Project, dep: String) {
        if (project.pluginManager.hasPlugin(PLUGIN_KMP)) {
            project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, dep)
        } else {
            project.dependencies.add(CONFIG_IMPL, dep)
        }
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
