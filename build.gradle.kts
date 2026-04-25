import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

apply(from = "publish.gradle.kts")

val ghostGroup = libs.versions.ghost.group.get()
val ghostVersion = libs.versions.ghost.version.get()

group = ghostGroup
version = ghostVersion

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    afterEvaluate {
        if (name != "ghostsample") {
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    // Configure test logging across all modules to show up in the console UI
    tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
        // Disable caching so tests always run and print their names to the console
        outputs.upToDateWhen { false }
        
        testLogging {
            // Disable default event logging to avoid duplicate prints and double line breaks
            // We handle the UI purely via the TestListener below
            showStandardStreams = false
            showExceptions = false
            showCauses = false
            showStackTraces = false
        }

        addTestListener(object : org.gradle.api.tasks.testing.TestListener {
            override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
            override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
            override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                if (result.resultType == org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE) {
                    val cleanName = testDescriptor.name.substringBefore("[")
                    println("\n  ❌ [FAIL] $cleanName")
                    result.exception?.let { e ->
                        println("       ↳ ${e.message}")
                    }
                }
            }
            override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                // Silenced: we use the Unified Table instead
            }
        })
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/"))
            username.set(project.findProperty("sonatypeUsername") as String?)
            password.set(project.findProperty("sonatypePassword") as String?)
            packageGroup.set(libs.versions.ghost.group.get())
        }
    }
}


