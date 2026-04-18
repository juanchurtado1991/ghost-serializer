plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ghost.api)
    implementation(libs.ghost.core)
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.ghost.compiler)

    testImplementation(libs.kotlin.test)
}
