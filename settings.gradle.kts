pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "GhostSerialization"

include(":serialization-api")
include(":serialization")
include(":serialization-compiler")
include(":serialization-retrofit")
include(":serialization-ktor")
include(":serialization-sample")
include(":serialization-benchmark")
include(":serialization-integration-test")