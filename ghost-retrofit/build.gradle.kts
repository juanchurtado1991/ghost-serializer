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
    
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
