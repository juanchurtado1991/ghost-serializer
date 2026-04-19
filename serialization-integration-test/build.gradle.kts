plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":serialization-api"))
    implementation(project(":serialization"))
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":serialization-compiler"))

    testImplementation(libs.kotlin.test)
}
