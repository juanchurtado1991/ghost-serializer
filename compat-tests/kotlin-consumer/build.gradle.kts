import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.google.devtools.ksp") version "2.3.12"
    id("com.android.kotlin.multiplatform.library") version "9.1.1"
    id("com.ghostserializer.ghost")
}

val ghostVersion = gradle.extra["ghostVersion"] as String

ghost {
    version = ghostVersion
}

kotlin {
    jvmToolchain(17)
    jvm()
    android {
        namespace = "compat.consumer"
        compileSdk = 36
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":models"))
            implementation("com.ghostserializer:ghost-ktor:$ghostVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

ksp {
    arg("ghost.moduleName", "compat")
}
