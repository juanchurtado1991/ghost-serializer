plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // FIXED: Downgraded from 21 to 17 to maximize library adoption across different CIs.
    jvmToolchain(17) 
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}