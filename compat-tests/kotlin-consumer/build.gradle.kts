plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.google.devtools.ksp") version "2.3.12"
    id("com.android.kotlin.multiplatform.library") version "9.1.1"
}

/** Ghost version under test: `-PghostVersion=…`, else the repo's own `publish-version`. */
val ghostVersion: String = providers.gradleProperty("ghostVersion").getOrElse(
    Regex("""publish-version\s*=\s*"([^"]+)"""")
        .find(rootDir.resolve("../../gradle/libs.versions.toml").readText())
        ?.groupValues?.get(1)
        ?: error("publish-version not found in gradle/libs.versions.toml")
)

kotlin {
    jvmToolchain(17)
    jvm()
    android {
        namespace = "compat.consumer"
        compileSdk = 36
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation("com.ghostserializer:ghost-serialization:$ghostVersion")
                implementation("com.ghostserializer:ghost-ktor:$ghostVersion")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

ksp {
    arg("ghost.moduleName", "compat")
}

dependencies {
    add("kspCommonMainMetadata", "com.ghostserializer:ghost-compiler:$ghostVersion")
}

tasks.configureEach {
    val runsAfterCommonKsp = name.startsWith("compile") || name.startsWith("ksp")
    if (runsAfterCommonKsp && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}
