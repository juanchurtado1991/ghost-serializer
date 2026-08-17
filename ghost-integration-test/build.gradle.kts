plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

// Canonical fixture lives in ghost-benchmark; share via sourceSets (no project dep —
// ghost-benchmark already depends on this module).
sourceSets {
    test {
        resources.srcDir(
            rootProject.layout.projectDirectory.dir("ghost-benchmark/src/main/resources"),
        )
    }
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-serialization"))
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":ghost-compiler"))
    kspTest(project(":ghost-compiler"))
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // Coverage-guided fuzzing (GhostComplexObjectFuzzTest) — requires the JUnit5 platform.
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.jazzer.junit)
    testRuntimeOnly(libs.junit.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // See the matching comment in ghost-serialization/build.gradle.kts: without this,
    // GhostComplexObjectFuzzTest's .cifuzz-corpus seeds are never replayed by a normal test run.
    environment("JAZZER_COVERAGE", "true")
}
