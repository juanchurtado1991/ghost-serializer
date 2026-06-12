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

    // JIT diagnostic mode: ./gradlew :ghost-benchmark:run -Pjit -PskipTests --args="..."
    // Writes HotSpot compilation log to ghost-benchmark/jit-compilation.log
    // Open the log with JITWatch (https://github.com/AdoptOpenJDK/jitwatch) to see:
    //   - Which methods were inlined (and why some were rejected)
    //   - Method bytecode sizes vs. JIT thresholds (MaxInlineSize, FreqInlineSize)
    //   - OSR (On-Stack Replacement) events in hot loops
    // Zero overhead in production — flags only apply to this forked JVM process.
    if (project.hasProperty("jit")) {
        val logFile = project.layout.projectDirectory.file("jit-compilation.log").asFile
        // Only LogCompilation (XML) — do NOT add PrintCompilation/PrintInlining here.
        // Those flags write to stdout synchronously on every JIT event, slowing the
        // benchmark 3-5x and invalidating throughput measurements. JITWatch reads
        // all inlining decisions (including rejection reasons) from the XML file.
        jvmArgs(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+LogCompilation",
            "-XX:LogFile=${logFile.absolutePath}",
        )
        println("🔬 JIT logging enabled → ${logFile.absolutePath}")
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
