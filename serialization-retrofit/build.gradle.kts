plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":serialization"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
}
