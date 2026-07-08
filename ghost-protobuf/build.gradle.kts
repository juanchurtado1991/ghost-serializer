import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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

android {
    namespace = "com.ghost.protobuf"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
