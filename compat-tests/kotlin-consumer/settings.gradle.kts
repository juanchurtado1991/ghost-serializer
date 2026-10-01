// Standalone consumer build (not part of the root build): compiles against Ghost artifacts
// published to mavenLocal, with the consumer's own Kotlin/KSP versions, to prove that the
// published klibs/metadata and the KSP-generated code work for the minimum supported Kotlin.
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "ghost-kotlin-consumer"
