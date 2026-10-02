import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.ghost.serialization.ktor"
        compileSdk = 36
        aarMetadata {
            minCompileSdk = libs.versions.android.min.compile.sdk.get().toInt()
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
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":ghost-serialization"))
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
        }
        jvmMain.dependencies {
            // Server ApplicationCall extensions live in jvmMain — Ktor server is JVM-only.
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

dependencies {
    add("kspCommonMainMetadata", project(":ghost-compiler"))
    add("kspJvm", project(":ghost-compiler"))
    add("kspAndroid", project(":ghost-compiler"))
}
