package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Identifiers shared by the Ghost compiler plugin: its id and command-line option (which must
 * match what `ghost-gradle-plugin` passes), and the runtime/annotation declarations it links.
 * [SERIALIZER_SUFFIX] and [NESTED_NAME_SEPARATOR] mirror the KSP processor's naming convention
 * for generated serializers (`<package>.<Outer_Inner>Serializer`).
 */
internal object GhostCompilerPluginConstants {
    const val PLUGIN_ID = "com.ghostserializer.ghost"

    const val OPTION_ENABLED = "enabled"
    const val OPTION_ENABLED_DESCRIPTION =
        "Links each @GhostSerialization class to its generated serializer for Kotlin/Native and Kotlin/Wasm lookup"
    const val OPTION_ENABLED_VALUE_DESCRIPTION = "<true|false>"
    const val UNKNOWN_OPTION_PREFIX = "Unknown Ghost compiler plugin option: "

    private const val KEY_ENABLED_NAME = "ghost.enabled"
    val KEY_ENABLED = CompilerConfigurationKey<Boolean>(KEY_ENABLED_NAME)

    private const val ANNOTATIONS_PACKAGE = "com.ghost.serialization.annotations"
    private const val CONTRACT_PACKAGE = "com.ghost.serialization.contract"

    val GHOST_PROTO_SERIALIZATION_ID = ClassId(
        packageFqName = FqName(fqName = ANNOTATIONS_PACKAGE),
        topLevelName = Name.identifier("GhostProtoSerialization")
    )
    val GHOST_SERIALIZATION_ID = ClassId(
        packageFqName = FqName(fqName = ANNOTATIONS_PACKAGE),
        topLevelName = Name.identifier("GhostSerialization")
    )
    val SERIALIZER_LINK_ID = ClassId(
        packageFqName = FqName(fqName = CONTRACT_PACKAGE),
        topLevelName = Name.identifier("GhostSerializerLink")
    )

    const val NESTED_NAME_SEPARATOR = "_"
    const val SERIALIZER_SUFFIX = "Serializer"

    const val MISSING_SERIALIZER_PREFIX = "Ghost: generated serializer "
    const val MISSING_SERIALIZER_INFIX = " not found for "
    const val MISSING_SERIALIZER_SUFFIX =
        "; automatic Kotlin/Native and Kotlin/Wasm registration is skipped for this class. " +
            "Check that KSP runs for this source set, or register the module with Ghost.addRegistry."
}
