import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
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
        moduleName = "ghost-sample"
        browser {
            val projectDir = project.projectDir
            commonWebpackConfig {
                outputFileName = "ghost-sample.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(
                    static = (devServer?.static ?: mutableListOf()).apply {
                        add(projectDir.path)
                    }
                )
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
            
            implementation(project(":serialization-api"))
            implementation(project(":serialization"))
            implementation(project(":serialization-ktor"))
            implementation(libs.ktorfit.lib)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.serialization.json)
        }
        
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.moshi)
            implementation(libs.moshi.kotlin)
            implementation(libs.gson)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.moshi)
            implementation(libs.moshi.kotlin)
            implementation(libs.gson)
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
    namespace = "com.ghostserializer.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ghostserializer.sample"
        minSdk = 24
        targetSdk = 35
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

dependencies {
    // Ghost KSP - Using catalog dependency for industrial consistency
    add("kspJvm", project(":serialization-compiler"))
    add("kspAndroid", project(":serialization-compiler"))
    add("kspIosArm64", project(":serialization-compiler"))
    add("kspIosSimulatorArm64", project(":serialization-compiler"))
    add("kspWasmJs", project(":serialization-compiler"))
    
    // Ktorfit KSP
    add("kspJvm", libs.ktorfit.ksp)
    add("kspAndroid", libs.ktorfit.ksp)
    add("kspIosArm64", libs.ktorfit.ksp)
    add("kspIosSimulatorArm64", libs.ktorfit.ksp)
    add("kspWasmJs", libs.ktorfit.ksp)
}

compose.desktop {
    application {
        mainClass = "com.ghostserializer.sample.MainKt"
    }
}