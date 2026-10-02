package com.ghost.gradle

import org.gradle.api.Project

/**
 * Detects Ktor/Retrofit on a project's classpath and injects the matching `ghost-ktor`/
 * `ghost-retrofit` adapter artifact — split out of [GhostPlugin] since this changes for a
 * different reason than wiring KSP/KMP/Android core dependencies does: adding support for a new
 * HTTP client library touches only [networkAdapters], never [GhostPlugin.apply].
 */
internal object GhostNetworkAutoInjector {

    fun wireNetworkAutoInjection(project: Project, extension: GhostExtension) {
        project.afterEvaluate {
            val version = extension.version.get()
            networkAdapters(extension = extension).forEach { adapter ->
                if (adapter.isEnabled && hasDependency(project = project, adapter = adapter)) {
                    injectNetworkDependency(
                        project = project,
                        dep = "$GROUP_ID:${adapter.artifactId}:$version"
                    )
                }
            }
        }
    }

    private fun hasDependency(project: Project, adapter: NetworkAdapter): Boolean {
        return listOf(CONFIG_IMPL, CONFIG_API, CONFIG_COMMON_MAIN_IMPL).any { name ->
            val config = project.configurations.findByName(name)
            config?.dependencies?.any {
                it.group == adapter.group && adapter.matchesName(it.name)
            } ?: false
        }
    }

    private fun injectNetworkDependency(project: Project, dep: String) {
        if (project.pluginManager.hasPlugin(PLUGIN_KMP)) {
            project.dependencies.add(CONFIG_COMMON_MAIN_IMPL, dep)
        } else {
            project.dependencies.add(CONFIG_IMPL, dep)
        }
    }

    /** The network adapters [wireNetworkAutoInjection] can auto-inject — adding a new one here
     * is the only change needed to detect and wire up another HTTP client library. */
    private fun networkAdapters(extension: GhostExtension): List<NetworkAdapter> = listOf(
        NetworkAdapter(
            artifactId = ARTIFACT_KTOR,
            isEnabled = extension.autoInjectKtor.get(),
            group = GROUP_KTOR,
            matchesName = { it.startsWith(PREFIX_KTOR_CLIENT) }
        ),
        NetworkAdapter(
            artifactId = ARTIFACT_RETROFIT,
            isEnabled = extension.autoInjectRetrofit.get(),
            group = GROUP_RETROFIT,
            matchesName = { it == NAME_RETROFIT }
        )
    )

    private const val PLUGIN_KMP = "org.jetbrains.kotlin.multiplatform"

    private const val GROUP_ID = "com.ghostserializer"
    private const val ARTIFACT_KTOR = "ghost-ktor"
    private const val ARTIFACT_RETROFIT = "ghost-retrofit"

    private const val CONFIG_COMMON_MAIN_IMPL = "commonMainImplementation"
    private const val CONFIG_IMPL = "implementation"
    private const val CONFIG_API = "api"

    private const val GROUP_KTOR = "io.ktor"
    private const val PREFIX_KTOR_CLIENT = "ktor-client"
    private const val GROUP_RETROFIT = "com.squareup.retrofit2"
    private const val NAME_RETROFIT = "retrofit"
}

/**
 * One HTTP client library [GhostPlugin] can auto-inject its adapter artifact for — [group]/
 * [matchesName] describe how to spot the library on the project's classpath, [isEnabled] is the
 * matching `GhostExtension` toggle. Adding a third client (beyond Ktor/Retrofit) means adding one
 * more entry to [GhostNetworkAutoInjector]'s `networkAdapters` list, not another branch in
 * [GhostPlugin.apply].
 */
private data class NetworkAdapter(
    val artifactId: String,
    val isEnabled: Boolean,
    val group: String,
    val matchesName: (String) -> Boolean
)
