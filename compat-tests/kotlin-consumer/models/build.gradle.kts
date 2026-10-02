import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// A separate module whose models are only used from the root project: proves the compiler-plugin
// link survives the klib boundary on Kotlin/Native and Kotlin/Wasm. It also uses the shared-metadata
// KSP layout (commonMain includes the metadata KSP output, as projects with other KSP processors do),
// so the Ghost Gradle plugin must not generate the serializers per target as well.
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("com.android.kotlin.multiplatform.library")
    id("com.ghostserializer.ghost")
}

ghost {
    version = gradle.extra["ghostVersion"] as String
}

kotlin {
    jvmToolchain(17)
    jvm()
    android {
        namespace = "compat.consumer.models"
        compileSdk = 35
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

tasks.configureEach {
    val runsAfterCommonKsp = name.startsWith("compile") || name.startsWith("ksp")
    if (runsAfterCommonKsp && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}

ksp {
    arg("ghost.moduleName", "compat_models")
}
