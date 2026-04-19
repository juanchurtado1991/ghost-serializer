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
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable() // API usually doesn't need this, but for KMP consistency
    }

    sourceSets {
        commonMain.dependencies { 
            // Core annotations only. Keep it zero-dependency.
        }
    }
}

android {
    namespace = "com.ghostserializer.api"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // FIXED: Standardized
        targetCompatibility = JavaVersion.VERSION_17
    }
}