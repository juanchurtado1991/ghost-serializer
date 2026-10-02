package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import com.ghost.serialization.compiler.plugin.GhostCompilerPluginConstants as C

/**
 * Registers [GhostSerializerLinkExtension] unless the `enabled` option was set to `false`.
 *
 * [pluginId] is deliberately not an `override`: Kotlin 2.4 added it to [CompilerPluginRegistrar] as
 * an abstract member, but 2.2.21 (which this plugin is compiled against) does not declare it. A
 * plain member still emits `getPluginId()`, which satisfies 2.4's abstract method at runtime, so one
 * artifact loads on both compilers (2.4 also checks it matches [GhostCommandLineProcessor.pluginId]).
 */
class GhostCompilerPluginRegistrar : CompilerPluginRegistrar() {

    val pluginId: String = C.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration
    ) {
        val isEnabled = configuration.get(C.KEY_ENABLED, true)
        if (!isEnabled) return
        GhostCompilerCompat.registerIrGenerationExtension(
            storage = this,
            extension = GhostSerializerLinkExtension(messageCollector = configuration.messageCollector)
        )
    }
}
