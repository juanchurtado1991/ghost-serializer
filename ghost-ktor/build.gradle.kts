import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
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
            api(project(":ghost-serialization"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            compileOnly(libs.ktor.server.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.ghost.serialization.ktor"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":ghost-compiler"))
    add("kspJvm", project(":ghost-compiler"))
    add("kspAndroid", project(":ghost-compiler"))
}
