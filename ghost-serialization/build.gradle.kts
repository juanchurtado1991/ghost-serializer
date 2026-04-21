import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
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
    androidTarget {
        publishLibraryVariants("release")
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "ghost-serialization-wasm"
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    js(IR) {
        moduleName = "ghost-serialization"
        browser()
        nodejs()
        binaries.library()
    }

    // Official NPM Tools Distribution
    val copyNpmTools by tasks.registering(Copy::class) {
        from("npm-tools")
        into(layout.buildDirectory.dir("dist/wasmJs/productionLibrary/tools"))
    }

    tasks.named("wasmJsBrowserProductionLibraryDistribution") {
        finalizedBy(copyNpmTools)
    }

    sourceSets {
        commonMain {
            // Add KSP output to commonMain so all platforms inherit it
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":ghost-api"))
                api(libs.okio)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ksp {
    arg("ghost.moduleName", "ghost_serialization")
}

dependencies {
    // Generate KSP ONLY once for common metadata
    // ALL platform targets will inherit the generated code from commonMain
    add("kspCommonMainMetadata", project(":ghost-compiler"))
}

// Fix implicit dependency issues globally and lazily
tasks.configureEach {
    val isSourcesJar = name.contains("sourcesJar", ignoreCase = true)
    if ((name.startsWith("compile") || name.startsWith("ksp") || isSourcesJar) && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
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
