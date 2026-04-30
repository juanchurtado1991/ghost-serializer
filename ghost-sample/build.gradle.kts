import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    jvm()
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GhostSample"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("ghost-sample-wasm")
        browser {
            val projectDir = project.projectDir
            commonWebpackConfig {
                outputFileName = "ghost-sample.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(projectDir.path)
                }
            }
        }
        binaries.executable()
    }
    
    // KSP automatically adds generated sources to the corresponding source sets.
    // Manual srcDir configuration is removed to prevent redeclaration errors.

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            
            // Ghost (Local Project for Development/Benchmark)
            implementation(project(":ghost-serialization"))
            implementation(project(":ghost-ktor"))
            
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.serialization.json)
        }
        
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.metrics)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.bundles.serialization.engines)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.bundles.serialization.engines)
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.ktor.client.core)
            }
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

android {
    namespace = "com.ghost.serialization.sample"
    compileSdk = 36

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    defaultConfig {
        applicationId = "com.ghost.serialization.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    
    buildFeatures {
        compose = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
            resources.srcDirs("src/androidMain/resources")
        }
    }
}

ksp {
    arg("ghost.moduleName", "serialization_sample")
}


configurations.all {
    resolutionStrategy {
        // Force JetBrains version of Lifecycle & SavedState to avoid KLIB conflicts with Google's version
        force(libs.androidx.lifecycle.common)
        force(libs.androidx.lifecycle.runtime)
        force(libs.androidx.lifecycle.runtime.compose)
        force(libs.androidx.lifecycle.viewmodel)
        force(libs.androidx.lifecycle.viewmodel.compose)
        force(libs.androidx.lifecycle.viewmodel.savedstate)
        force(libs.androidx.savedstate)
        force(libs.androidx.savedstate.compose)
    }
}

dependencies {
    // Ghost KSP (Local Project for Development/Benchmark)
    add("kspCommonMainMetadata", project(":ghost-compiler"))
    add("kspJvm", project(":ghost-compiler"))
    add("kspAndroid", project(":ghost-compiler"))
    add("kspIosArm64", project(":ghost-compiler"))
    add("kspIosSimulatorArm64", project(":ghost-compiler"))
    add("kspWasmJs", project(":ghost-compiler"))

}

compose.desktop {
    application {
        mainClass = "com.ghost.serialization.sample.MainKt"
    }
}