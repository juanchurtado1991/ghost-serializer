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
        outputModuleName.set("ghost-serialization-wasm")
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        
        // Industrial NPM Package Configuration
        val ghostVersion = rootProject.version.toString()
        compilations.named("main") {
            packageJson {
                customField("version", ghostVersion)
                customField("description", "Ghost Serialization WASM Engine - High Performance Multiplatform Serialization")
                customField("author", "Ghost Team")
                customField("license", "Apache-2.0")
                customField("repository", mapOf("type" to "git", "url" to "https://github.com/juanchurtado1991/GhostSerialization"))
                customField("bin", mapOf("ghost-sync" to "./tools/ghost-transpiler.js"))
            }
        }
    }
    js(IR) {
        outputModuleName.set("ghost-serialization")
        browser()
        nodejs()
        binaries.library()
    }

    // Official NPM Tools & Meta Distribution
    val copyNpmMeta by tasks.registering(Copy::class) {
        from("npm-tools")
        into(layout.buildDirectory.dir("dist/wasmJs/productionLibrary/tools"))
        
        // Ensure transpiler is executable
        doLast {
            val transpiler = File("${layout.buildDirectory.get().asFile}/dist/wasmJs/productionLibrary/tools/ghost-transpiler.js")
            if (transpiler.exists()) {
                transpiler.setExecutable(true)
            }
        }
    }
    
    val copyNpmDocs by tasks.registering(Copy::class) {
        from(rootProject.file("README.md"))
        from(rootProject.file("CHANGELOG.md"))
        from(rootProject.file("LICENSE"))
        into(layout.buildDirectory.dir("dist/wasmJs/productionLibrary"))
    }

    tasks.named("wasmJsBrowserProductionLibraryDistribution") {
        finalizedBy(copyNpmMeta, copyNpmDocs)
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
