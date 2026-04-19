plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.GhostBenchmarkKt")
}

tasks.withType<org.gradle.jvm.application.tasks.CreateStartScripts> {
    dependsOn(":ghost-serialization:jvmTestClasses", ":ghost-integration-test:testClasses")
}

tasks.named<JavaExec>("run") {
    dependsOn(":ghost-serialization:jvmTestClasses", ":ghost-integration-test:testClasses")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-serialization"))
    implementation(project(":ghost-integration-test"))
    implementation(libs.gson)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)

    implementation(libs.junit.launcher)
    implementation(libs.junit.platform.engine)
    runtimeOnly(libs.junit.engine)
    runtimeOnly(libs.junit.vintage.engine)
    runtimeOnly(libs.kotlin.test.junit5)
    runtimeOnly(libs.kotlin.test)
    
    // Include test classes for auto-detection (Safe Root-Relative paths)
    runtimeOnly(files("${rootDir.absolutePath}/ghost-serialization/build/classes/kotlin/jvm/test"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-serialization/build/generated/ksp/jvm/kotlin"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/classes/kotlin/test"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/classes/kotlin/main"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/generated/ksp/main/kotlin"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/generated/ksp/main/resources"))
}

// Include KSP generated resources
sourceSets.main {
    resources.srcDir("build/generated/ksp/main/resources")
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
