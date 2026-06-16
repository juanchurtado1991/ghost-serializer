import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        publishLibraryVariants("release")
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":ghost-api"))
                api(libs.okio)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ksp { arg("ghost.moduleName", "ghost_serialization") }

dependencies {
    // KSP runs once on common metadata; all platform targets inherit the generated code
    add("kspCommonMainMetadata", project(":ghost-compiler"))
}

// Ensure KSP metadata runs before any compile or sourcesJar task
tasks.configureEach {
    val isSourcesJar = name.contains("sourcesJar", ignoreCase = true)
    if ((name.startsWith("compile") || name.startsWith("ksp") || isSourcesJar) && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}

android {
    namespace = "com.ghost.serialization"
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
