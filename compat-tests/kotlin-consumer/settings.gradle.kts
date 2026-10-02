// Standalone consumer build (not part of the root build): compiles against Ghost artifacts
// published to mavenLocal, with the consumer's own Kotlin/KSP versions, to prove that the
// published klibs/metadata, the KSP-generated code and the Ghost Gradle plugin (including
// automatic Kotlin/Native and Kotlin/Wasm registration across modules) work for the minimum
// supported Kotlin.
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.ghostserializer.ghost") {
                useVersion(gradle.extra["ghostVersion"] as String)
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

/** Ghost version under test: `-PghostVersion=…`, else the repo's own `publish-version`. */
gradle.extra["ghostVersion"] = providers.gradleProperty("ghostVersion").getOrElse(
    Regex("""publish-version\s*=\s*"([^"]+)"""")
        .find(settingsDir.resolve("../../gradle/libs.versions.toml").readText())
        ?.groupValues?.get(1)
        ?: error("publish-version not found in gradle/libs.versions.toml")
)

rootProject.name = "ghost-kotlin-consumer"
include(":models")
