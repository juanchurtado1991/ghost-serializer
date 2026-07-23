import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
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
            implementation(project(":ghost-protobuf"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            compileOnly(libs.ktor.server.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            // Server-side (ApplicationCall) extension tests need a real routing/response
            // pipeline — JVM-only, test-only, doesn't affect the compileOnly server dependency.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.test.host)
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
