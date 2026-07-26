import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.ghost.protobuf"
        compileSdk = 36
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Reference implementation used as a correctness oracle for proto3 JSON
            // conformance tests (ProtoJsonConformanceTest) — JVM-only, test-only.
            implementation(libs.protobuf.java)
            implementation(libs.protobuf.java.util)
            // Coverage-guided fuzzing (ProtoWktFuzzTest) — requires the JUnit5 platform,
            // see useJUnitPlatform() below.
            implementation(libs.kotlin.test.junit5)
            implementation(libs.jazzer.junit)
            runtimeOnly(libs.junit.engine)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
