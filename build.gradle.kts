import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions { 
            jvmTarget = "17" 
            freeCompilerArgs += "-Xexpect-actual-classes" // Required for KMP 2.x sometimes
        }
    }
}