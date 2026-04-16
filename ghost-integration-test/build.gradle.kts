plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-core"))
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":ghost-compiler"))

    testImplementation(libs.kotlin.test)
}
