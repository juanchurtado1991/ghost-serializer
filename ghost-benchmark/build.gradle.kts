plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.GhostBenchmarkKt")
}

tasks.withType<CreateStartScripts> {
    dependsOn(
        ":ghost-serialization:jvmTestClasses",
        ":ghost-integration-test:testClasses",
        ":ghost-ktor:jvmTestClasses",
        ":ghost-retrofit:testClasses",
        ":ghost-spring-boot-starter:testClasses",
        ":ghost-gradle-plugin:testClasses",
    )
}

tasks.named<JavaExec>("run") {
    // Runs root ciTest (ciTestJvm + Android unit + iOS on macOS). Add new :module:test in
    // ciTestJvmModules (build.gradle.kts), not here. -PskipTests skips the suite.
    if (!project.hasProperty("skipTests")) {
        dependsOn(":ciTest")
    }
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
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okio)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
}

// Include KSP generated resources
sourceSets.main {
    resources.srcDir("build/generated/ksp/main/resources")
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
