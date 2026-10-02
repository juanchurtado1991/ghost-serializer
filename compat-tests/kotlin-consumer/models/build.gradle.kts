import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// A separate module whose models are only used from the root project: proves the compiler-plugin
// link survives the klib boundary on Kotlin/Native and Kotlin/Wasm.
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
        compileSdk = 36
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
}

ksp {
    arg("ghost.moduleName", "compat_models")
}
