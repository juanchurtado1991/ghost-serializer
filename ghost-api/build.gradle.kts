import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.ghost.serialization.api"
        compileSdk = 36
        optimization {
            consumerKeepRules.file("ghost-proguard-rules.pro")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
    }

    sourceSets {
        commonMain.dependencies {
            // Core annotations and types only. Keep runtime deps zero.
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
