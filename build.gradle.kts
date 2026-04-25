import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    id("org.jetbrains.dokka") version "2.2.0"
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
                    freeCompilerArgs.addAll(
                        "-Xexpect-actual-classes",
                        "-Xexplicit-backing-fields",
                        "-Xreturn-value-checker=full"
                    )
                }
            }
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


