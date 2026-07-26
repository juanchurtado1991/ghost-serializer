import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.ghost.serialization"
        compileSdk = 36
        minSdk = 21
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // AGP 9's com.android.kotlin.multiplatform.library disables Android unit tests by
        // default (single-variant architecture) — opt back in to restore what used to run as
        // testDebugUnitTest under the old com.android.library plugin.
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":ghost-api"))
                api(libs.okio)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ksp { arg("ghost.moduleName", "ghost_serialization") }

dependencies {
    // KSP runs once on common metadata; all platform targets inherit the generated code
    add("kspCommonMainMetadata", project(":ghost-compiler"))
}

// Ensure KSP metadata runs before any compile or sourcesJar task
tasks.configureEach {
    val isSourcesJar = name.contains("sourcesJar", ignoreCase = true)
    if ((name.startsWith("compile") || name.startsWith("ksp") || isSourcesJar) && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}
