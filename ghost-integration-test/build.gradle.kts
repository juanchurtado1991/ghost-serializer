plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-serialization"))
    implementation(project(":ghost-protobuf"))
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":ghost-compiler"))
    kspTest(project(":ghost-compiler"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
