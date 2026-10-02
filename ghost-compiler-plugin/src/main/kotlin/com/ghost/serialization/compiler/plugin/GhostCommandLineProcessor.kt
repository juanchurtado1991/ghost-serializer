package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import com.ghost.serialization.compiler.plugin.GhostCompilerPluginConstants as C

/** Parses the `-P plugin:com.ghostserializer.ghost:enabled=<true|false>` option set by the Gradle plugin. */
class GhostCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = C.PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = C.OPTION_ENABLED,
            valueDescription = C.OPTION_ENABLED_VALUE_DESCRIPTION,
            description = C.OPTION_ENABLED_DESCRIPTION,
            required = false
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            C.OPTION_ENABLED -> configuration.put(C.KEY_ENABLED, value.toBooleanStrict())
            else -> throw CliOptionProcessingException(message = C.UNKNOWN_OPTION_PREFIX + option.optionName)
        }
    }
}
