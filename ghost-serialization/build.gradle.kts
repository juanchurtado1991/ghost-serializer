import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
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
    jvm {
        withSourcesJar()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "ghost-serialization-wasm"
        browser()
        binaries.library()
    }
    js(IR) {
        moduleName = "ghost-serialization"
        browser()
        nodejs()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":ghost-api"))
            api(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    sourceSets.configureEach {
        kotlin.srcDir("build/generated/ksp/$name/kotlin")
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":ghost-compiler"))
    add("kspJvm", project(":ghost-compiler"))
    add("kspAndroid", project(":ghost-compiler"))
}

android {
    namespace = "com.ghost.serialization.core"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }
}

tasks.named("wasmJsPublicPackageJson") {
    doLast {
        val packageJsonFile = file("build/tmp/wasmJsPublicPackageJson/package.json")
        if (packageJsonFile.exists()) {
            val content = packageJsonFile.readText()
            val updatedContent = content.replace(
                "\"name\": \"GhostSerialization-ghost-serialization-wasm-js\"",
                "\"name\": \"ghost-serialization\""
            )
            packageJsonFile.writeText(updatedContent)
        }
    }
}
