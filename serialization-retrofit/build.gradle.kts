plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ghost.core)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
}
