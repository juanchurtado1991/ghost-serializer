plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.GhostBenchmarkKt")
}

tasks.withType<org.gradle.jvm.application.tasks.CreateStartScripts> {
    dependsOn(":serialization:jvmTestClasses", ":serialization-integration-test:testClasses")
}

tasks.named<JavaExec>("run") {
    dependsOn(":serialization:jvmTestClasses", ":serialization-integration-test:testClasses")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":serialization-api"))
    implementation(project(":serialization"))
    implementation(project(":serialization-integration-test"))
    implementation(libs.gson)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    
    // JUnit for Automated Safety Audit (Separated according to Senior Audit)
    implementation(libs.junit.launcher)
    implementation(libs.junit.platform.engine)
    runtimeOnly(libs.junit.engine)
    runtimeOnly(libs.junit.vintage.engine)
    runtimeOnly(libs.kotlin.test.junit5)
    runtimeOnly(libs.kotlin.test)
    
    // Include test classes for auto-detection (Safe Root-Relative paths)
    runtimeOnly(files("${rootDir.absolutePath}/serialization/build/classes/kotlin/jvm/test"))
    runtimeOnly(files("${rootDir.absolutePath}/serialization/build/generated/ksp/jvm/kotlin"))
    runtimeOnly(files("${rootDir.absolutePath}/serialization-integration-test/build/classes/kotlin/test"))
    runtimeOnly(files("${rootDir.absolutePath}/serialization-integration-test/build/classes/kotlin/main"))
    runtimeOnly(files("${rootDir.absolutePath}/serialization-integration-test/build/generated/ksp/main/kotlin"))
    runtimeOnly(files("${rootDir.absolutePath}/serialization-integration-test/build/generated/ksp/main/resources"))
}

// Include KSP generated resources
sourceSets.main {
    resources.srcDir("build/generated/ksp/main/resources")
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
