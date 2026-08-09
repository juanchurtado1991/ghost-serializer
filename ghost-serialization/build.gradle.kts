import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.ghost.serialization"
        compileSdk = 36
        minSdk = 21
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // AGP 9's com.android.kotlin.multiplatform.library disables Android unit tests by
        // default (single-variant architecture) — opt back in to restore what used to run as
        // testDebugUnitTest under the old com.android.library plugin.
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    jvm {
        withSourcesJar()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                api(project(":ghost-api"))
                api(libs.okio)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // Reference implementation used as a correctness oracle for proto3 JSON
            // conformance tests (ProtoJsonConformanceTest) — JVM-only, test-only.
            implementation(libs.protobuf.java)
            implementation(libs.protobuf.java.util)
            // Coverage-guided fuzzing (ProtoWktFuzzTest) — requires the JUnit5 platform.
            implementation(libs.kotlin.test.junit5)
            implementation(libs.jazzer.junit)
            runtimeOnly(libs.junit.engine)
            // Reference JSON tree used only to parse yaml-test-suite's in.json fixtures for the
            // value-conformance check (GhostYamlTestSuiteConformanceTest) — JVM-only, test-only,
            // not a Ghost runtime dependency.
            implementation(libs.kotlinx.serialization.json)
            // DynamicTest/TestFactory for the data-driven yaml-test-suite harness — JVM-only.
            implementation(libs.junit.jupiter.api)
            // Independent second YAML parser used as a validation oracle for
            // GhostYamlFlatWriter's output (GhostYamlWriterConformanceTest) — JVM-only,
            // test-only, not a Ghost runtime dependency.
            implementation(libs.kaml)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

tasks.register<JavaExec>("yamlComplianceMatrix") {
    group = "verification"
    description = "Prints Ghost's YAML spec-compliance report against the vendored yaml-test-suite snapshot"
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("com.ghost.serialization.yaml.testsuite.YamlComplianceReportKt")
}

tasks.register<JavaExec>("yamlWriterComplianceMatrix") {
    group = "verification"
    description = "Prints Ghost's YAML writer conformance report (round-trip + kaml oracle) against the vendored yaml-test-suite snapshot"
    dependsOn("jvmTestClasses")
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("com.ghost.serialization.yaml.testsuite.YamlWriterComplianceReportKt")
}

ksp { arg("ghost.moduleName", "ghost_serialization") }

dependencies {
    // KSP runs once on common metadata; all platform targets inherit the generated code
    add("kspCommonMainMetadata", project(":ghost-compiler"))
}

// Ensure KSP metadata runs before any compile or sourcesJar task
tasks.configureEach {
    val isSourcesJar = name.contains("sourcesJar", ignoreCase = true)
    if ((name.startsWith("compile") || name.startsWith("ksp") || isSourcesJar) && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}
