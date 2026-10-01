package com.ghost.gradle

/** Plugin ids, Gradle configuration names, artifact names and sample coordinates shared by the plugin tests. */
internal object GhostPluginTestConstants {
    const val PLUGIN_GHOST = "com.ghostserializer.ghost"
    const val PLUGIN_KSP = "com.google.devtools.ksp"
    const val PLUGIN_KOTLIN_JVM = "org.jetbrains.kotlin.jvm"
    const val PLUGIN_KOTLIN_MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"

    const val CONFIG_IMPLEMENTATION = "implementation"
    const val CONFIG_COMMON_MAIN_IMPLEMENTATION = "commonMainImplementation"

    const val ARTIFACT_API = "ghost-api"
    const val ARTIFACT_COMPILER = "ghost-compiler"
    const val ARTIFACT_KTOR = "ghost-ktor"
    const val ARTIFACT_RETROFIT = "ghost-retrofit"
    const val ARTIFACT_SERIALIZATION = "ghost-serialization"

    const val KTOR_CLIENT_COORDINATE = "io.ktor:ktor-client-core:3.5.1"
    const val RETROFIT_COORDINATE = "com.squareup.retrofit2:retrofit:2.9.0"

    const val TARGET_JVM = "jvm"
    const val TASK_KSP_KOTLIN = "kspKotlin"
    const val SOURCE_DIR = "src/main/kotlin/com/example"
    const val MODEL_FILE = "Model.kt"
}
