plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
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