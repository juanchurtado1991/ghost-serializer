import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies { 
            // Core annotations only. Keep it zero-dependency.
        }
    }
}

android {
    namespace = "com.ghost.serialization.api"
    compileSdk = 36

    defaultConfig {
        consumerProguardFiles("ghost-proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}