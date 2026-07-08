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
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "GhostSerialization"

include(":ghost-api")
include(":ghost-serialization")
include(":ghost-compiler")
include(":ghost-retrofit")
include(":ghost-ktor")
include(":ghost-sample")
include(":ghost-benchmark")
include(":ghost-integration-test")
include(":ghost-gradle-plugin")
include(":ghost-spring-boot-starter")
include(":ghost-protobuf")