plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.GhostBenchmarkKt")
}

tasks.withType<org.gradle.jvm.application.tasks.CreateStartScripts> {
    dependsOn(
        ":ghost-serialization:jvmTestClasses",
        ":ghost-integration-test:testClasses",
        ":ghost-ktor:jvmTestClasses",
        ":ghost-gradle-plugin:testClasses"
    )
}

tasks.named<JavaExec>("run") {
    // Run ALL test suites before benchmark: JVM, WASM, Integration, Ktor, Plugin
    if (!project.hasProperty("skipTests")) {
        dependsOn(
            ":ghost-serialization:jvmTest",
            ":ghost-integration-test:test",
            ":ghost-ktor:jvmTest",
            ":ghost-gradle-plugin:test"
        )
    }

    /**
     * Optional Async Profiler (CPU flame graph): download a release from
     * https://github.com/async-profiler/async-profiler/releases and point to the native library:
     *
     *   export GHOST_ASYNC_PROFILER=/path/to/async-profiler/build/libasyncProfiler.so
     *   ./gradlew :ghost-benchmark:run
     *
     * Or: ./gradlew :ghost-benchmark:run -Pghost.asyncProfiler=/path/to/libasyncProfiler.so
     *
     * Output: ghost-benchmark/build/async-profiler.html — open in a browser (FlameGraph).
     * Note: the profile includes tests + benchmark (whole run task). For a cleaner graph of only
     * the benchmark JVM, run installDist and launch the script with the same -agentpath.
     */
    val profilerLib = System.getenv("GHOST_ASYNC_PROFILER")
        ?: project.findProperty("ghost.asyncProfiler")?.toString()
    if (profilerLib != null) {
        val outFile = layout.buildDirectory.get().asFile.resolve("async-profiler.html")
        jvmArgs(
            "-agentpath:${file(profilerLib).absolutePath}=start,event=cpu,file=${outFile.absolutePath},interval=500000"
        )
        doFirst {
            logger.lifecycle("Async Profiler enabled → CPU profile will be written to: ${outFile.absolutePath}")
        }
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
