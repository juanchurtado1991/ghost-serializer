buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.30.0")
    }
}

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val localProperties = java.util.Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
    localProperties.forEach { (key, value) ->
        extra.set(key as String, value)
    }
}

val ghostGroup = libs.versions.publish.group.get()
val ghostVersion = libs.versions.publish.version.get()

group = ghostGroup
version = ghostVersion

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    tasks.withType<AbstractTestTask>().configureEach {
        outputs.upToDateWhen { false }

        testLogging {
            showStandardStreams = false
            showExceptions = false
            showCauses = false
            showStackTraces = false
        }

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(
                testDescriptor: TestDescriptor,
                result: TestResult
            ) {
                if (result.resultType == TestResult.ResultType.FAILURE) {
                    val cleanName = testDescriptor.name.substringBefore("[")
                    println("\n  ❌ [FAIL] $cleanName")
                    result.exception?.let { e ->
                        println("       ↳ ${e.message}")
                    }
                }
            }

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult
            ) {
                // Silenced: we use the Unified Table instead
            }
        })
    }
    apply(from = "$rootDir/publish.gradle")
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/"))
            username.set(project.findProperty("sonatypeUsername") as String?)
            password.set(project.findProperty("sonatypePassword") as String?)
            packageGroup.set(libs.versions.publish.group.get())
        }
    }
}

val isMacOsHost = OperatingSystem.current().isMacOsX

tasks.register("publishToGitHubPackages") {
    group = "publishing"
    description =
        "Publish all Ghost modules to GitHub Packages (requires gpr.user and gpr.key in local.properties)"

    subprojects.forEach { subproject ->
        val isPublishable = subproject.name.startsWith("ghost") && 
                           !subproject.name.contains("sample") && 
                           !subproject.name.contains("benchmark") && 
                           !subproject.name.contains("integration-test")
        if (isPublishable) {
            dependsOn(subproject.tasks.matching { it.name == "publishAllPublicationsToGitHubPackagesRepository" })
        }
    }
}

val ciTestIosSkipped = tasks.register("ciTestIosSkipped") {
    group = "verification"
    description = "Logs when iOS CI tests are skipped on non-macOS hosts"
    val isMac = isMacOsHost
    onlyIf { !isMac }
    doLast {
        println(
            "ciTest: skipping :ghost-serialization:iosSimulatorArm64Test (requires macOS + Xcode)"
        )
    }
}

val ciTestIos = tasks.register("ciTestIos") {
    group = "verification"
    description = "iOS simulator tests (same as CI test-ios job; macOS only)"
    val isMac = isMacOsHost
    onlyIf { isMac }
    dependsOn(
        ":ghost-serialization:kspCommonMainKotlinMetadata",
        ":ghost-serialization:iosSimulatorArm64Test",
    )
}

/**
 * JVM test modules run in GitHub Actions `test-jvm` and before `:ghost-benchmark:run` (via [ciTest]).
 * When you add a new `:module:test`, append it here — do not wire CI/benchmark separately.
 */
val ciTestJvmModules = listOf(
    ":ghost-serialization:jvmTest",
    ":ghost-ktor:jvmTest",
    ":ghost-compiler:test",
    ":ghost-integration-test:test",
    ":ghost-retrofit:test",
    ":ghost-spring-boot-starter:test",
    ":ghost-gradle-plugin:test",
    ":ghost-protobuf:jvmTest",
)

val ciTestJvm = tasks.register("ciTestJvm") {
    group = "verification"
    description = "JVM test modules (CI test-jvm job; included in ciTest and ghost-benchmark:run)"
    ciTestJvmModules.forEach { dependsOn(it) }
}

tasks.register("ciTest") {
    group = "verification"
    description = "Full CI test suite (GitHub Actions); iOS auto-skipped on Linux/Windows"
    dependsOn(
        ciTestIosSkipped,
        ciTestIos,
        ciTestJvm,
        ":ghost-serialization:testDebugUnitTest",
    )
}

/** Alias for [ciTest] — run every test module before benchmarks or releases. */
tasks.register("allTests") {
    group = "verification"
    description = "All tests (JVM + Android + iOS on macOS) — same as ciTest"
    dependsOn("ciTest")
}

tasks.register("verifyAndBenchmark") {
    group = "verification"
    description = "allTests then :ghost-benchmark:benchmarkRegression (full, ~9 min bench)"
    dependsOn("ciTest", ":ghost-benchmark:benchmarkRegression")
}

tasks.register("verifyAndBenchmarkFast") {
    group = "verification"
    description = "allTests then :ghost-benchmark:benchmarkRegressionFast (~2 min bench)"
    dependsOn("ciTest", ":ghost-benchmark:benchmarkRegressionFast")
}
