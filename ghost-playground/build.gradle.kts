import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "ghostPlayground.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(project(":ghost-serialization"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.moshi)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.moshi)
        }
    }
}

ksp {
    arg("ghost.moduleName", "playground")
    arg("ghost.textChannel", "true")
}

dependencies {
    add("kspCommonMainMetadata", project(":ghost-compiler"))
    add("kspJvm", libs.moshi.kotlin.codegen)
}

tasks.configureEach {
    val isSourcesJar = name.contains("sourcesJar", ignoreCase = true)
    if ((name.startsWith("compile") || name.startsWith("ksp") || isSourcesJar) &&
        name != "kspCommonMainKotlinMetadata"
    ) {
        dependsOn(tasks.matching { it.name == "kspCommonMainKotlinMetadata" })
    }
}

/**
 * Publish the production Wasm distribution into repo [docs/] for GitHub Pages
 * (`https://juanchurtado1991.github.io/ghost-serializer/`), preserving wiki,
 * coverage, and Ghost manuals.
 */
tasks.register("publishToDocs") {
    group = "documentation"
    description = "Copy wasmJs productionExecutable into docs/ for GitHub Pages"
    dependsOn("wasmJsBrowserDistribution")

    doLast {
        val dist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile
        require(dist.isDirectory) { "Missing distribution at ${dist.absolutePath}" }

        val docs = rootProject.layout.projectDirectory.dir("docs").asFile
        docs.mkdirs()

        val preserve = setOf(
            "wiki",
            "coverage",
            "GHOST_MANUAL_EN.md",
            "Ghost-Serialization-Manual-1.3.0.pdf",
            ".nojekyll",
        )

        docs.listFiles()?.forEach { child ->
            if (child.name !in preserve) {
                child.deleteRecursively()
            }
        }

        dist.copyRecursively(docs, overwrite = true)

        // Keep Pages lean — source maps are for local debugging only.
        docs.walkTopDown().filter { it.isFile && it.name.endsWith(".map") }.forEach { it.delete() }

        val resourceDir = project.file("src/wasmJsMain/resources")
        listOf("index.html", "sitemap.xml", "robots.txt").forEach { name ->
            val src = resourceDir.resolve(name)
            if (src.exists()) {
                src.copyTo(docs.resolve(name), overwrite = true)
            } else if (name == "index.html" && !docs.resolve("index.html").exists()) {
                error("Missing index.html for Ghost Playground Pages site")
            }
        }

        docs.resolve(".nojekyll").writeText("")
        println("Published Ghost Playground to ${docs.absolutePath}")
    }
}
