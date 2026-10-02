package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import com.ghost.serialization.compiler.plugin.GhostCompilerPluginConstants as C

/** Registers [GhostSerializerLinkExtension] unless the `enabled` option was set to `false`. */
class GhostCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration
    ) {
        val isEnabled = configuration.get(C.KEY_ENABLED, true)
        if (!isEnabled) return
        IrGenerationExtension.registerExtension(
            extension = GhostSerializerLinkExtension(messageCollector = configuration.messageCollector)
        )
    }
}
