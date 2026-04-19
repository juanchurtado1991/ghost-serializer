plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-serialization"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
}
