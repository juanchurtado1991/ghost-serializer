plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.BenchmarkLauncherKt")
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

fun JavaExec.configureBenchmarkJvm(profile: String = "full") {
    systemProperty("ghost.benchmark.profile", profile)
    val logFile = project.layout.projectDirectory.file("jit-compilation.log").asFile
    if (project.hasProperty("jit")) {
        jvmArgs(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+LogCompilation",
            "-XX:LogFile=${logFile.absolutePath}",
        )
        println("🔬 JIT logging enabled → ${logFile.absolutePath}")
    }
}

/** Runs `:allTests` before benchmarks unless `-PskipTests` is set. */
fun Task.configureBenchmarkTestGate() {
    if (!project.hasProperty("skipTests")) {
        dependsOn(":allTests")
    }
}

fun registerBenchmarkTask(
    name: String,
    suite: String,
    description: String,
    profile: String = "full",
) {
    tasks.register<JavaExec>(name) {
        group = "benchmark"
        this.description = description
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.ghost.benchmark.BenchmarkLauncherKt")
        args(suite)
        dependsOn("classes")
        configureBenchmarkTestGate()
        configureBenchmarkJvm(profile)
    }
}

registerBenchmarkTask(
    "benchmarkTwitter",
    "twitter",
    "Twitter macro Ghost vs KSER + regression gate (~3 min full)",
)
registerBenchmarkTask(
    "benchmarkSynthetic",
    "synthetic",
    "LIST / SYNC / WRITING synthetic harness + regression gate (~6 min full)",
)
registerBenchmarkTask(
    "benchmarkTwitterFast",
    "twitter",
    "Twitter macro fast regression gate (~30s)",
    profile = "fast",
)
registerBenchmarkTask(
    "benchmarkSyntheticFast",
    "synthetic",
    "Synthetic fast regression gate (~60s)",
    profile = "fast",
)
registerBenchmarkTask(
    "benchmarkSpecial",
    "special",
    "Ghost-only special features micro-benchmarks",
)
registerBenchmarkTask(
    "benchmarkRawJson",
    "rawjson",
    "Ghost-only RawJson byte vs string channels",
)

tasks.register("benchmarkRegression") {
    group = "benchmark"
    description = "Twitter + synthetic + special regression gates, full profile (~9 min); runs allTests first unless -PskipTests"
    dependsOn("benchmarkTwitter", "benchmarkSynthetic", "benchmarkSpecial")
    configureBenchmarkTestGate()
}

tasks.register("benchmarkRegressionFast") {
    group = "benchmark"
    description = "Twitter + synthetic + special regression gates, fast profile (~1–2 min); runs allTests first unless -PskipTests"
    dependsOn("benchmarkTwitterFast", "benchmarkSyntheticFast", "benchmarkSpecial")
    configureBenchmarkTestGate()
}

tasks.named<JavaExec>("run") {
    args("full")
    configureBenchmarkTestGate()
    configureBenchmarkJvm()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-serialization"))
    implementation(project(":ghost-integration-test"))
    implementation(project(":ghost-protobuf"))
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okio)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
}

sourceSets.main {
    resources.srcDir("build/generated/ksp/main/resources")
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
