plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = libs.versions.jvmTarget.get() }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies { 
            // Core annotations only. Keep it zero-dependency.
        }
    }
}

android {
    namespace = "com.ghost.serialization.api"
    compileSdk = 35 // FIXED: Stable SDK

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // FIXED: Standardized
        targetCompatibility = JavaVersion.VERSION_17
    }
}